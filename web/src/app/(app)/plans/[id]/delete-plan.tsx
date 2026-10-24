"use client";

import { useState, useTransition } from "react";
import { deletePlan } from "@/app/actions/plans";
import { Button } from "@/components/ui";

export function DeletePlan({ planId, isActive }: { planId: string; isActive: boolean }) {
  const [confirming, setConfirming] = useState(false);
  const [pending, start] = useTransition();
  if (!confirming) {
    return (
      <Button variant="danger" onClick={() => setConfirming(true)}>
        Delete
      </Button>
    );
  }
  return (
    <div className="flex items-center gap-2">
      <span className="text-sm text-muted">{isActive ? "Delete your active plan?" : "Delete this plan?"} Past workouts stay.</span>
      <Button variant="danger" disabled={pending} onClick={() => start(() => deletePlan(planId))}>
        {pending ? "Deleting…" : "Delete"}
      </Button>
      <Button variant="ghost" onClick={() => setConfirming(false)}>Cancel</Button>
    </div>
  );
}
