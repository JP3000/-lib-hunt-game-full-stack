import { AuthGate } from "@/components/auth-gate";
import { LevelPage } from "@/components/level-page";

export default function Level09Page() {
  return (
    <AuthGate>
      <LevelPage levelNumber={9} />
    </AuthGate>
  );
}
