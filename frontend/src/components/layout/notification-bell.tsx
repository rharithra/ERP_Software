import { Bell } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { notificationApi, type AppNotification } from "@/lib/api";
import { formatDay } from "@/lib/pipeline";

function linkFor(item: AppNotification) {
  if (item.entityType === "FOLLOW_UP") return "/app/follow-ups";
  if (item.entityType === "QUOTATION" && item.entityId) return `/app/quotations/${item.entityId}`;
  if (item.entityType === "SALE" && item.entityId) return `/app/sales/${item.entityId}`;
  if (item.entityType === "LEAD" && item.entityId) return `/app/leads/${item.entityId}`;
  return "/app/pipeline";
}

export function NotificationBell() {
  const [items, setItems] = useState<AppNotification[]>([]);
  const [unread, setUnread] = useState(0);

  async function refresh() {
    try {
      const [list, count] = await Promise.all([notificationApi.list(), notificationApi.unreadCount()]);
      setItems(list);
      setUnread(count.count);
    } catch {
      /* cashiers and guests still get a bell; empty is fine */
    }
  }

  useEffect(() => {
    void refresh();
    const timer = window.setInterval(() => void refresh(), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  async function mark(id: string) {
    await notificationApi.markRead(id);
    await refresh();
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="icon" aria-label="Notifications" className="relative">
          <Bell className="h-4 w-4" />
          {unread > 0 ? (
            <span className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] text-primary-foreground">
              {unread > 9 ? "9+" : unread}
            </span>
          ) : null}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80">
        <DropdownMenuLabel className="flex items-center justify-between">
          Notifications
          {unread > 0 ? (
            <button
              type="button"
              className="text-xs font-normal text-primary"
              onClick={() => void notificationApi.markAllRead().then(refresh)}
            >
              Mark all read
            </button>
          ) : null}
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {items.length === 0 ? (
          <p className="px-2 py-6 text-center text-sm text-muted-foreground">No in-app alerts yet.</p>
        ) : (
          items.slice(0, 12).map((item) => (
            <DropdownMenuItem key={item.id} asChild className="items-start">
              <Link to={linkFor(item)} onClick={() => void mark(item.id)}>
                <span className="block">
                  <span className="block text-sm font-medium">{item.title}</span>
                  <span className="block text-xs text-muted-foreground">{item.body}</span>
                  <span className="block text-[11px] text-muted-foreground">{formatDay(item.createdAt)}</span>
                </span>
              </Link>
            </DropdownMenuItem>
          ))
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
