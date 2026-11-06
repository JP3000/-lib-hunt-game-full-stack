import { AuthGate } from "@/components/auth-gate";
import { LevelPage } from "@/components/level-page";

export default function Level01Page() {
  return (
    <AuthGate>
      <LevelPage levelNumber={1} />
    </AuthGate>
  );
}
