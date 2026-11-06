import { AuthGate } from "@/components/auth-gate";
import { LevelPage } from "@/components/level-page";

export default function Level11Page() {
  return (
    <AuthGate>
      <LevelPage levelNumber={11} />
    </AuthGate>
  );
}
