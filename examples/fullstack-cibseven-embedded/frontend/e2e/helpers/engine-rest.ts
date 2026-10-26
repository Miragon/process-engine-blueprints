import { expect, type APIRequestContext } from "@playwright/test";

/**
 * Thin helpers over the CIB seven `/engine-rest` API for the things the UI legitimately can't drive:
 * firing timers (14-day waits compressed to a click) and completing the form-only `clarify-return`
 * task. This is the same split the Bruno collections use — every *business* action goes through the
 * React app; timers and the deliberately UI-less task go through the engine here.
 */

const ENGINE_REST = process.env.ENGINE_REST ?? "http://localhost:8080/engine-rest";

/** Resolves the process-instance id for the application's business key. */
export async function processInstanceId(
  request: APIRequestContext,
  applicationId: string,
): Promise<string> {
  const response = await request.get(
    `${ENGINE_REST}/process-instance?businessKey=${applicationId}`,
  );
  expect(response.ok()).toBeTruthy();
  const instances = (await response.json()) as Array<{ id: string }>;
  expect(instances.length, `no running instance for ${applicationId}`).toBeGreaterThan(0);
  return instances[0].id;
}

/** Fires the timer job waiting on the given activity, so the case leaves that wait state. */
export async function fireTimer(
  request: APIRequestContext,
  applicationId: string,
  activityId: string,
): Promise<void> {
  const instanceId = await processInstanceId(request, applicationId);
  await expect(async () => {
    const jobs = await request.get(
      `${ENGINE_REST}/job?processInstanceId=${instanceId}&activityId=${activityId}&timers=true`,
    );
    const list = (await jobs.json()) as Array<{ id: string }>;
    expect(list.length, `timer ${activityId} not yet waiting`).toBeGreaterThan(0);
    const executed = await request.post(`${ENGINE_REST}/job/${list[0].id}/execute`);
    expect(executed.status()).toBe(204);
  }).toPass({ timeout: 15_000 });
}

/**
 * Waits until the process instance has an execution parked at the given activity — i.e. it has
 * reached that wait state. Use this before an action that must compensate earlier steps: the UI
 * status badge is driven by an async read-model projection and can flip to "bestellt" a beat before
 * the engine has committed the order/contract/insurance activities and registered their compensation
 * boundaries. Withdrawing in that window makes the saga skip `cancelBikeOrder` (and its
 * `clarify-return` task) entirely. Mirrors the deliberate pre-withdraw wait in bruno/03-abort.
 */
export async function waitForWaitState(
  request: APIRequestContext,
  applicationId: string,
  activityId: string,
): Promise<void> {
  const instanceId = await processInstanceId(request, applicationId);
  await expect(async () => {
    const executions = await request.get(
      `${ENGINE_REST}/execution?processInstanceId=${instanceId}&activityId=${activityId}`,
    );
    const list = (await executions.json()) as Array<{ id: string }>;
    expect(list.length, `not yet parked at ${activityId}`).toBeGreaterThan(0);
  }).toPass({ timeout: 15_000 });
}

/**
 * Completes an open user task through the engine (the same as a human submitting its Camunda Form).
 * Used only for `userTask_clarifyReturn`, which has no UI on purpose.
 */
export async function completeTask(
  request: APIRequestContext,
  applicationId: string,
  taskDefinitionKey: string,
  variables: Record<string, { value: unknown; type: string }> = {},
): Promise<void> {
  await expect(async () => {
    const tasks = await request.get(
      `${ENGINE_REST}/task?processInstanceBusinessKey=${applicationId}&taskDefinitionKey=${taskDefinitionKey}`,
    );
    const list = (await tasks.json()) as Array<{ id: string }>;
    expect(list.length, `task ${taskDefinitionKey} not yet waiting`).toBeGreaterThan(0);
    const completed = await request.post(`${ENGINE_REST}/task/${list[0].id}/complete`, {
      data: { variables },
    });
    expect(completed.status()).toBe(204);
  }).toPass({ timeout: 15_000 });
}
