import { createFileRoute } from "@tanstack/react-router";
import { TaskInboxPage } from "@/pages/task-inbox";

export const Route = createFileRoute("/aufgaben")({
  component: TaskInboxPage,
});
