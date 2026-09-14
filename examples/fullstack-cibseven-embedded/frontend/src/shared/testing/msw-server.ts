import { setupServer } from "msw/node";
import { getMiraVeloBikeLeasingAPIMock } from "@/shared/api/generated/endpoints.msw";

/**
 * A node MSW server seeded with orval's generated default handlers. Tests override individual
 * endpoints with `server.use(getListLeasingApplicationsMockHandler(...))` etc.
 */
export const server = setupServer(...getMiraVeloBikeLeasingAPIMock());
