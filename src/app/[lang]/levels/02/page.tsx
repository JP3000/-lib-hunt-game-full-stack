import { AuthGate } from "@/components/auth-gate";
import { LevelPage } from "@/components/level-page";

export default function Level02Page() {
  return (
    <AuthGate>
      <LevelPage levelNumber={2} />
    </AuthGate>
  );
}
