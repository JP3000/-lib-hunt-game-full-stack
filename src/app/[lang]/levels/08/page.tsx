import { AuthGate } from "@/components/auth-gate";
import { LevelPage } from "@/components/level-page";

export default function Level08Page() {
  return (
    <AuthGate>
      <LevelPage levelNumber={8} />
    </AuthGate>
  );
}
