import { AuthGate } from "@/components/auth-gate";
import { LevelPage } from "@/components/level-page";

export default function Level07Page() {
  return (
    <AuthGate>
      <LevelPage levelNumber={7} />
    </AuthGate>
  );
}
