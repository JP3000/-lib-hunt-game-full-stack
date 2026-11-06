import { AuthGate } from "@/components/auth-gate";
import { LevelPage } from "@/components/level-page";

export default function Level12Page() {
  return (
    <AuthGate>
      <LevelPage levelNumber={12} />
    </AuthGate>
  );
}
