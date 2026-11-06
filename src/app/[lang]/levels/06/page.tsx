import { AuthGate } from "@/components/auth-gate";
import { LevelPage } from "@/components/level-page";

export default function Level06Page() {
  return (
    <AuthGate>
      <LevelPage levelNumber={6} />
    </AuthGate>
  );
}
