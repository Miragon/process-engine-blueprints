import { useCopy } from "@/shared/i18n";
import { SubmitLeasingForm } from "@/features/submit-leasing-request";

/** Router-free: the route passes navigation to the created application. */
export function SubmitApplicationPage({
  onCreated,
}: {
  onCreated: (applicationId: string) => void;
}) {
  const copy = useCopy();
  return (
    <section className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-h1 text-schwarz">{copy.submit.title}</h1>
        <p className="text-lead text-schwarz/60">{copy.submit.subtitle}</p>
      </header>
      <SubmitLeasingForm onCreated={onCreated} />
    </section>
  );
}
