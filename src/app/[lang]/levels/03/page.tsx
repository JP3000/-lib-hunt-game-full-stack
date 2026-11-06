import { AuthGate } from "@/components/auth-gate";
import { LevelPage } from "@/components/level-page";

export default function Level03Page() {
  return (
    <AuthGate>
      <LevelPage levelNumber={3} />
    </AuthGate>
  );
}
