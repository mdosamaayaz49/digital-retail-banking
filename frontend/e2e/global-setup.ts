import { request } from "@playwright/test";
export default async function setup() {
  if (!process.env.DEMO_PASSWORD) return;
  const client = await request.newContext();
  try {
    for (const port of [8080, 8081, 8082, 8083, 8084, 8085, 8086]) {
      const deadline = Date.now() + 120000;
      let ready = false;
      while (Date.now() < deadline) {
        try {
          const response = await client.get(
            "http://localhost:" + port + "/actuator/health/readiness",
            { timeout: 2000 },
          );
          if (response.ok()) {
            ready = true;
            break;
          }
        } catch {
          /* A process may still be starting; enforce the bounded deadline below. */
        }
        await new Promise((r) => setTimeout(r, 1000));
      }
      if (!ready)
        throw new Error(
          "Banking service on port " +
            port +
            " did not become ready. Check its runtime log.",
        );
    }
  } finally {
    await client.dispose();
  }
}
