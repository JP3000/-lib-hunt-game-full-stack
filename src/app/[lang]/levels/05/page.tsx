import { AuthGate } from "@/components/auth-gate";
import { LevelPage } from "@/components/level-page";

export default function Level05Page() {
  return (
    <AuthGate>
      <LevelPage levelNumber={5} />
    </AuthGate>
  );
}
