import { copy } from "@/shared/i18n";
import type { BikeDto } from "@/shared/api/generated/model";

/** The label shown in a bike picker — unavailable bikes stay selectable but are marked as such. */
export function bikeOptionLabel(bike: BikeDto): string {
  return bike.available ? bike.model : `${bike.model} (${copy.submit.bikeUnavailableSuffix})`;
}
