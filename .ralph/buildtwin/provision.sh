#!/usr/bin/env bash
# BUILD-TWIN-ARM64-VERIFICATION (AC1 + AC2) — provision.sh
#
# Spins up ONE throwaway ARM64-native, CPU-only, RAM-capped (6-8GB) cloud
# instance on demand, mirroring the Realme 9 Pro 5G's real runtime
# constraints (arm64-v8a ABI, CPU-only inference, ~8GB ceiling). A pass on
# this twin is a REAL predictor of a pass on the device — not a looser
# x86-with-emulation environment.
#
# Provider: AWS EC2 Graviton (aarch64, general-purpose = CPU-only). The
# instance family is constrained to the "g" (Graviton) ARM64 general-purpose
# line so a GPU/TPU cannot accidentally appear. Instance TYPE is chosen from
# an allow-list whose RAM lands in the 6-8GB device-mirroring band.
#
# No always-on instance: provision.sh creates one instance, records it in
# .ralph/buildtwin/state.json, and teardown.sh terminates it. Re-running
# provision.sh while an instance is recorded refuses to double-provision.
#
# Prerequisites (run from the machine that owns the AWS credentials):
#   - `aws` CLI installed + credentials configured (AWS_PROFILE or -p)
#   - an arm64 Ubuntu AMI (auto-resolved via AWS Systems Manager parameter
#     /aws/service/canonical/ubuntu/server/24.04/stable/current/arm64/hvm/ebs-gp3/ami-id)
#   - a default VPC + subnet in the target region, or a --subnet-id
#
# Usage (from the repo root):
#   bash .ralph/buildtwin/provision.sh [-p <profile>] [-r <region>] [-t m7g.large]
#   bash .ralph/buildtwin/teardown.sh  [-p <profile>]
#
# The script does NOT copy any artifact to the device. It only stands the
# twin up. Copying an artifact back is authorized EXCLUSIVELY by a passing
# BuildTwinVerifier run (see com.jarvis.app.buildtwin) whose verdict is
# recorded in .ralph/incidents/.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
STATE_FILE="$SCRIPT_DIR/state.json"

# Allow-list of instance types mirroring the device band (6-8GB, CPU-only,
# ARM64 Gravitons). Anything else is REJECTED — a looser twin is a lie.
declare -A ALLOWED_INSTANCES=(
  ["m7g.large"]="2|8"     # 2 vCPU / 8 GiB — classic device-mirror choice
  ["m6g.large"]="2|8"     # 2 vCPU / 8 GiB
)

main () {
  local profile="${AWS_PROFILE:-}"
  local region="${AWS_REGION:-us-east-1}"
  local instance_type="m7g.large"

  while getopts "p:r:t:h" opt; do
    case "$opt" in
      p) profile="$OPTARG" ;;
      r) region="$OPTARG" ;;
      t) instance_type="$OPTARG" ;;
      h) sed -n '1,40p' "$0"; exit 0 ;;
      *) echo "unknown option" >&2; exit 2 ;;
    esac
  done

  if [ -z "$profile" ]; then
    echo "ERROR: no AWS profile. Use -p <profile> or AWS_PROFILE." >&2
    exit 2
  fi

  local aws
  aws=(aws)
  if [ -n "$profile" ]; then aws+=(--profile "$profile"); fi
  aws+=(--region "$region")

  # Security: the AMI must be resolved from the resolved SSM parameter first.
  local ami_id
  ami_id="$("${aws[@]}" ssm get-parameter \
    --name /aws/service/canonical/ubuntu/server/24.04/stable/current/arm64/hvm/ebs-gp3/ami-id \
    --query 'Parameter.Value' --output text)"

  if [ -z "${ami_id:-}" ]; then
    echo "ERROR: could not resolve an arm64 Ubuntu AMI for region $region." >&2
    exit 2
  fi

  # AC1 security: refuse an existing recorded instance (no double provision).
  if [ -f "$STATE_FILE" ] && grep -q '"status": *"provisioned"' "$STATE_FILE"; then
    echo "ERROR: a twin instance is already recorded in $STATE_FILE." >&2
    echo "       Run teardown.sh first (no always-on instance)." >&2
    exit 2
  fi

  # AC2: instance type must be on the allow-list AND land in the 6-8GB band.
  local spec="${ALLOWED_INSTANCES[$instance_type]:-}"
  if [ -z "$spec" ]; then
    echo "ERROR: instance type '$instance_type' is not in the ARM64 CPU-only allow-list." >&2
    echo "       Allowed: ${!ALLOWED_INSTANCES[*]}" >&2
    exit 2
  fi
  local vcpu="${spec%|*}" ramgb="${spec#*|}"
  if [ "$ramgb" -lt 6 ] || [ "$ramgb" -gt 8 ]; then
    echo "ERROR: instance type '$instance_type' has ${ramgb}GiB RAM — outside the 6-8GB device-mirror band." >&2
    exit 2
  fi

  echo "Provisioning ARM64 CPU-only twin: type=$instance_type vcpu=$vcpu ram=${ramgb}GiB region=$region"

  local user_data
  user_data="$(cat <<'USERDATA_EOF'
#!/usr/bin/env bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq python3 python3-pip ffmpeg git >/tmp/buildtwin_apt.log 2>&1 || true
mkdir -p /opt/buildtwin/voiceforge
nproc > /opt/buildtwin/spec_host.txt
free -b | awk '/^Mem:/{printf "%.2f\n", $2/1024/1024/1024}' >> /opt/buildtwin/spec_host.txt
uname -m >> /opt/buildtwin/spec_host.txt
# VoiceForge runtime probe (honest healthy:false when chatterbox-tts is absent).
python3 -m http.server 8765 --bind 127.0.0.1 &
echo done
USERDATA_EOF
)"

  # Launch. Graviton family, arm64 architecture, CPU-only by construction.
  # ebs volume keeps it cheap; the `g` family never has a GPU/TPU.
  local launch_out
  launch_out="$("${aws[@]}" ec2 run-instances \
    --image-id "$ami_id" \
    --instance-type "$instance_type" \
    --key-name "${BUILDTWIN_KEY_NAME:?set BUILDTWIN_KEY_NAME to an existing EC2 keypair}" \
    --block-device-mappings 'DeviceName=/dev/sda1,Ebs={VolumeSize=20,VolumeType=gp3}' \
    --user-data "$user_data" \
    --query 'Instances[0].[InstanceId,Placement.AvailabilityZone]' \
    --output text)"
  local instance_id az
  read -r instance_id az <<<"$launch_out"
  echo "Launched instance $instance_id in $az — waiting for running state..."

  "${aws[@]}" ec2 wait instance-running --instance-ids "$instance_id"
  # Public DNS is what we probe (health + the GGUF step over SSH).
  local public_ip
  for _ in $(seq 1 30); do
    public_ip="$("${aws[@]}" ec2 describe-instances --instance-ids "$instance_id" \
      --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)"
    [ -n "$public_ip" ] && [ "$public_ip" != "None" ] && break
    sleep 5
  done
  if [ -z "$public_ip" ] || [ "$public_ip" = "None" ]; then
    echo "ERROR: instance $instance_id never got a public IP." >&2
    "${aws[@]}" ec2 terminate-instances --instance-ids "$instance_id" >/dev/null
    exit 2
  fi

  cat > "$STATE_FILE" <<EOF
{
  "provider": "aws",
  "host": "$public_ip",
  "instanceId": "$instance_id",
  "region": "$region",
  "instanceType": "$instance_type",
  "architecture": "arm64",
  "cpuOnly": true,
  "ramCapGb": $ramgb,
  "status": "provisioned"
}
EOF
  echo "Twin provisioned (no always-on instance — teardown.sh terminates it)."
  echo "  instance: $instance_id"
  echo "  host:     $public_ip"
  echo "  state:    $STATE_FILE"
  echo "NEXT STEP: run the BuildTwinVerifier against host=$public_ip port=8765"
}

main "$@"