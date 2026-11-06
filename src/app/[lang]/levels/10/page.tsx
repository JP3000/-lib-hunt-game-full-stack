import { AuthGate } from "@/components/auth-gate";
import { LevelPage } from "@/components/level-page";

export default function Level10Page() {
  return (
    <AuthGate>
      <LevelPage levelNumber={10} />
    </AuthGate>
  );
}
