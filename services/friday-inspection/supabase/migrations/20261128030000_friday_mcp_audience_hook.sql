-- Bind OAuth-issued tokens from this dedicated Friday Auth project to the Friday MCP resource.
-- Direct owner sign-in sessions do not contain an OAuth client_id and retain their normal audience.
create or replace function public.friday_mcp_access_token_hook(event jsonb)
returns jsonb
language plpgsql
stable
set search_path = ''
as $$
declare
  claims jsonb := coalesce(event->'claims', '{}'::jsonb);
  auth_method text := event->>'authentication_method';
begin
  if auth_method in ('oauth', 'oauth_provider/authorization_code', 'token_refresh')
     and jsonb_typeof(claims->'client_id') = 'string'
     and length(claims->>'client_id') > 0 then
    claims := jsonb_set(
      claims,
      '{aud}',
      to_jsonb('https://friday-inspection-production.up.railway.app/mcp'::text),
      true
    );
  end if;

  return jsonb_build_object('claims', claims);
end;
$$;

revoke all on function public.friday_mcp_access_token_hook(jsonb) from public;
revoke all on function public.friday_mcp_access_token_hook(jsonb) from anon, authenticated;
grant usage on schema public to supabase_auth_admin;
grant execute on function public.friday_mcp_access_token_hook(jsonb) to supabase_auth_admin;
