import { AuthGate } from "@/components/auth-gate";
import { LevelPage } from "@/components/level-page";

export default function Level04Page() {
  return (
    <AuthGate>
      <LevelPage levelNumber={4} />
    </AuthGate>
  );
}
