/-
  Friday action-outcome contract.

  This model deliberately separates issuing an action from observing and
  independently verifying its postcondition. It proves properties of this
  abstract transition system only. Kotlin/C++ conformance is a separate gate.
-/

namespace FridayFormal

inductive Phase where
  | requested
  | planned
  | executing
  | observed
  | verified
  | completed
  deriving DecidableEq, Repr

structure TaskState where
  phase : Phase
  postconditionObserved : Bool := false
  independentCheckPassed : Bool := false
  deriving DecidableEq, Repr

def initial : TaskState :=
  { phase := Phase.requested }

inductive Transition : TaskState → TaskState → Prop where
  | plan (s : TaskState) (h : s.phase = Phase.requested) :
      Transition s { s with phase := Phase.planned }
  | beginExecution (s : TaskState) (h : s.phase = Phase.planned) :
      Transition s { s with phase := Phase.executing }
  | observePostcondition (s : TaskState) (h : s.phase = Phase.executing) :
      Transition s { s with phase := Phase.observed, postconditionObserved := true }
  | independentlyVerify (s : TaskState)
      (hPhase : s.phase = Phase.observed)
      (hObserved : s.postconditionObserved = true) :
      Transition s { s with phase := Phase.verified, independentCheckPassed := true }
  | complete (s : TaskState)
      (hPhase : s.phase = Phase.verified)
      (hObserved : s.postconditionObserved = true)
      (hIndependent : s.independentCheckPassed = true) :
      Transition s { s with phase := Phase.completed }

def HasCompletionEvidence (s : TaskState) : Prop :=
  s.phase = Phase.completed →
    s.postconditionObserved = true ∧ s.independentCheckPassed = true

theorem transition_completed_has_evidence
    {before after : TaskState}
    (hStep : Transition before after)
    (hCompleted : after.phase = Phase.completed) :
    after.postconditionObserved = true ∧ after.independentCheckPassed = true := by
  cases hStep <;> simp_all [Transition]

inductive Reachable (start : TaskState) : TaskState → Prop where
  | refl : Reachable start start
  | step {before after : TaskState} :
      Reachable start before →
      Transition before after →
      Reachable start after

theorem reachable_completed_has_evidence
    (s : TaskState)
    (hReachable : Reachable initial s) :
    HasCompletionEvidence s := by
  induction hReachable with
  | refl =>
      intro hCompleted
      simp [initial] at hCompleted
  | step hPrevious hStep ih =>
      intro hCompleted
      exact transition_completed_has_evidence hStep hCompleted

theorem execution_cannot_skip_verification
    (s t : TaskState)
    (hExecuting : s.phase = Phase.executing)
    (hStep : Transition s t)
    (hCompleted : t.phase = Phase.completed) :
    False := by
  cases hStep <;> simp_all [Transition]

end FridayFormal
