# News feed connection (V1.0.0)

Open **News Intelligence → Calendar feed settings** and enter an authorized HTTPS JSON endpoint. The app refreshes on entry and every 15 minutes while the screen is open. **Refresh news & calendar** requests an immediate update. A failed calendar request retains the last successfully loaded calendar for the current session and shows the error. Official publisher news feeds continue to refresh independently.

The endpoint must return an object with `events` and `articles` arrays. Event `date` values require an ISO 8601 UTC offset, such as `2026-10-12T12:30:00Z`; device-local time is used for display and Today/This week grouping.

```json
{
  "events": [{"id":"event-1","title":"Central bank meeting","date":"2026-10-12T12:30:00Z","currency":"USD","impact":"High","forecast":"","previous":"","actual":""}],
  "articles": [{"id":"article-1","headline":"Market headline","summary":"Verified summary","symbol":"EURUSD","category":"FOREX","source":"Publisher","publishedAt":"2026-10-12T11:00:00Z"}]
}
```

High-impact, timed events create a notification 15 minutes before their scheduled time. Exact delivery needs Android's exact-alarm access; the app shows when that access is unavailable. Android notification permission is also required. Alarms are scheduled from the last feed response and do not refresh the feed while the app is closed. A new feed response reschedules matching alarms.

Forex Factory calendar data requires permission for reuse under its published notices. Connect a feed for which you have the necessary rights; no Forex Factory endpoint or credentials are bundled with this app.
