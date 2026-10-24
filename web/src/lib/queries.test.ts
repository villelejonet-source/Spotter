import { describe, expect, it } from "vitest";
import * as q from "./queries";

/** A fake PostgREST client that records the chain each query builds and answers with canned rows. */
function mockClient(rows: Record<string, unknown[]> = {}, count = 0) {
  const calls: { view: string; chain: [string, ...unknown[]][] }[] = [];
  const client: q.QueryClient = {
    from(view) {
      const chain: [string, ...unknown[]][] = [];
      calls.push({ view, chain });
      const builder = {
        select: (...a: unknown[]) => record("select", a),
        eq: (...a: unknown[]) => record("eq", a),
        gt: (...a: unknown[]) => record("gt", a),
        gte: (...a: unknown[]) => record("gte", a),
        lt: (...a: unknown[]) => record("lt", a),
        lte: (...a: unknown[]) => record("lte", a),
        in: (...a: unknown[]) => record("in", a),
        is: (...a: unknown[]) => record("is", a),
        not: (...a: unknown[]) => record("not", a),
        or: (...a: unknown[]) => record("or", a),
        order: (...a: unknown[]) => record("order", a),
        limit: (...a: unknown[]) => record("limit", a),
        maybeSingle: async () => ({ data: (rows[view]?.[0] as Record<string, unknown>) ?? null, error: null }),
        then: (resolve: (v: { data: unknown[]; error: null; count: number }) => unknown) => Promise.resolve({ data: rows[view] ?? [], error: null, count }).then(resolve),
      };
      function record(name: string, args: unknown[]) {
        chain.push([name, ...args]);
        return builder;
      }
      return builder as unknown as ReturnType<q.QueryClient["from"]>;
    },
  };
  return { client, calls, chainOf: (view: string) => calls.find((c) => c.view === view)?.chain ?? [] };
}

describe("sessions", () => {
  it("asks for one ordered page when a limit is given", async () => {
    const m = mockClient();
    await q.sessions(m.client, { since: 100, until: 200, limit: 5 });
    expect(m.chainOf("v_session_summary")).toEqual([
      ["select", "*"],
      ["gte", "started_at", 100],
      ["lt", "started_at", 200],
      ["order", "started_at", { ascending: false }],
      ["limit", 5],
    ]);
  });

  it("pages by started_at and returns newest first otherwise", async () => {
    const m = mockClient({ v_session_summary: [{ started_at: 1 }, { started_at: 2 }] });
    const rows = await q.sessions(m.client, { since: 50 });
    expect(rows.map((r) => r.started_at)).toEqual([2, 1]);
    expect(m.chainOf("v_session_summary")).toEqual([["select", "*"], ["gte", "started_at", 50], ["order", "started_at"], ["order", "sync_id"], ["limit", 1000]]);
  });

  it("loads only three columns for session times and counts finished ones without rows", async () => {
    const m = mockClient({}, 42);
    await q.sessionTimes(m.client, 7);
    expect(m.chainOf("v_session_summary")[0]).toEqual(["select", "sync_id,started_at,ended_at"]);
    const n = mockClient({}, 42);
    expect(await q.finishedSessionCount(n.client)).toBe(42);
    expect(n.chainOf("v_session_summary")).toEqual([["select", "sync_id", { count: "exact", head: true }], ["not", "ended_at", "is", null]]);
  });
});

describe("selectAll", () => {
  it("continues after the last key of a full page", async () => {
    const page = Array.from({ length: 1000 }, (_, i) => ({ sync_id: `id${i}` }));
    let served = 0;
    const client: q.QueryClient = {
      from: () => {
        const chain: unknown[][] = [];
        const b = {
          select: () => b,
          gt: (...a: unknown[]) => (chain.push(["gt", ...a]), b),
          order: () => b,
          limit: () => b,
          then: (resolve: (v: unknown) => unknown) => Promise.resolve({ data: served++ === 0 ? page : [{ sync_id: "last" }], error: null }).then(resolve),
          chain,
        };
        return b as unknown as ReturnType<q.QueryClient["from"]>;
      },
    };
    const rows = await q.selectAll<{ sync_id: string }>(client, "v_plan");
    expect(rows).toHaveLength(1001);
    expect(served).toBe(2);
  });

  it("breaks ties on a repeating key with sync_id, so a tie across pages isn't skipped", async () => {
    // A full page ending at started_at 5 / sync_id "m"; the next page must ask for (5, > "m") as well as > 5.
    const page = Array.from({ length: 1000 }, (_, i) => ({ started_at: i < 999 ? i : 5, sync_id: i < 999 ? `a${i}` : "m" }));
    const chains: unknown[][][] = [];
    let served = 0;
    const client: q.QueryClient = {
      from: () => {
        const chain: unknown[][] = [];
        chains.push(chain);
        const b = {
          select: () => b,
          gte: () => b,
          or: (...a: unknown[]) => (chain.push(["or", ...a]), b),
          gt: (...a: unknown[]) => (chain.push(["gt", ...a]), b),
          order: (...a: unknown[]) => (chain.push(["order", ...a]), b),
          limit: () => b,
          then: (resolve: (v: unknown) => unknown) => Promise.resolve({ data: served++ === 0 ? page : [{ started_at: 5, sync_id: "z" }], error: null }).then(resolve),
        };
        return b as unknown as ReturnType<q.QueryClient["from"]>;
      },
    };
    const rows = await q.sessions(client, { since: 0 });
    expect(rows).toHaveLength(1001);
    expect(chains[0]).toEqual([["order", "started_at"], ["order", "sync_id"]]);
    expect(chains[1]).toEqual([["or", "started_at.gt.5,and(started_at.eq.5,sync_id.gt.m)"], ["order", "started_at"], ["order", "sync_id"]]);
  });

  it("throws with the view's name when the server answers with an error", async () => {
    const client: q.QueryClient = {
      from: () => {
        const b = { select: () => b, order: () => b, limit: () => b, then: (r: (v: unknown) => unknown) => Promise.resolve({ data: null, error: { message: "boom" } }).then(r) };
        return b as unknown as ReturnType<q.QueryClient["from"]>;
      },
    };
    await expect(q.selectAll(client, "v_plan")).rejects.toThrow("v_plan: boom");
  });
});

describe("set history and records", () => {
  it("filters finished workouts by exercise and date on the server, with chosen columns", async () => {
    const m = mockClient();
    await q.setHistory(m.client, { exerciseId: "squat", since: 10, columns: q.PROGRESS_SET_COLUMNS });
    expect(m.chainOf("v_set_history")).toEqual([
      ["select", q.PROGRESS_SET_COLUMNS],
      ["eq", "session_finished", true],
      ["eq", "exercise_id", "squat"],
      ["gte", "session_started_at", 10],
      ["order", "sync_id"],
      ["limit", 1000],
    ]);
  });

  it("includes running workouts only when asked (the export)", async () => {
    const m = mockClient();
    await q.setHistory(m.client, { includeRunning: true });
    expect(m.chainOf("v_set_history")).toEqual([["select", "*"], ["order", "sync_id"], ["limit", 1000]]);
  });

  it("reads exercise usage from the grouped view", async () => {
    const m = mockClient({ v_exercise_usage: [{ exercise_id: "squat", session_count: "3" }] });
    expect(await q.exerciseUsage(m.client)).toEqual(new Map([["squat", 3]]));
    expect(m.chainOf("v_exercise_usage")[0]).toEqual(["select", "exercise_id,session_count"]);
  });

  it("takes the newest records with a limit, or per exercise", async () => {
    const m = mockClient();
    await q.personalRecords(m.client, { limit: 5 });
    expect(m.chainOf("v_personal_record")).toEqual([["select", "*"], ["order", "achieved_at", { ascending: false }], ["limit", 5]]);
    const n = mockClient({ v_personal_record: [{ achieved_at: 1 }, { achieved_at: 3 }, { achieved_at: 2 }] });
    const rows = await q.personalRecords(n.client, { exerciseId: "squat", since: 0 });
    expect(rows.map((r) => r.achieved_at)).toEqual([3, 2, 1]);
    expect(n.chainOf("v_personal_record").slice(1, 3)).toEqual([["eq", "exercise_id", "squat"], ["gte", "achieved_at", 0]]);
  });
});

describe("schedule and plans", () => {
  it("filters scheduled workouts to a day range and open ones", async () => {
    const m = mockClient();
    await q.scheduled(m.client, { fromDay: 20_000, toDay: 20_030, open: true, limit: 1 });
    expect(m.chainOf("v_scheduled_session")).toEqual([
      ["select", "*"],
      ["gte", "epoch_day", 20_000],
      ["lte", "epoch_day", 20_030],
      ["is", "session_id", null],
      ["order", "epoch_day", { ascending: true }],
      ["limit", 1],
    ]);
  });

  it("loads one plan with only its days and their exercises", async () => {
    const m = mockClient({
      v_plan: [{ sync_id: "p1", is_active: true, created_at: 1 }],
      v_plan_day: [{ sync_id: "d2", plan_id: "p1", position: 1 }, { sync_id: "d1", plan_id: "p1", position: 0 }],
      v_plan_exercise: [{ sync_id: "e1", plan_day_id: "d1", position: 0 }],
    });
    const plan = await q.plan(m.client, "p1");
    expect(plan?.days.map((d) => d.sync_id)).toEqual(["d1", "d2"]);
    expect(plan?.days[0].exercises).toHaveLength(1);
    expect(m.chainOf("v_plan")).toEqual([["select", "*"], ["eq", "sync_id", "p1"]]);
    expect(m.chainOf("v_plan_day")[1]).toEqual(["in", "plan_id", ["p1"]]);
    expect(m.chainOf("v_plan_exercise")[1]).toEqual(["in", "plan_day_id", ["d2", "d1"]]);
  });

  it("returns null for a plan that isn't there without loading days", async () => {
    const m = mockClient();
    expect(await q.plan(m.client, "nope")).toBeNull();
    expect(m.calls.map((c) => c.view)).toEqual(["v_plan"]);
  });

  it("finds the active plan with a one-row page", async () => {
    const m = mockClient({ v_plan: [{ sync_id: "p1", is_active: true, created_at: 1 }] });
    expect((await q.activePlan(m.client))?.sync_id).toBe("p1");
    expect(m.chainOf("v_plan")).toEqual([["select", "*"], ["eq", "is_active", true], ["order", "created_at", { ascending: false }], ["limit", 1]]);
  });
});
