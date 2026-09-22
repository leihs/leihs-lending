(ns leihs.lending.client.components.entities.user-popover
  (:require
   ["@@/popover" :refer [Popover PopoverContent PopoverTrigger]]
   ["lucide-react" :refer [UserRound UserX]]
   ["react-i18next" :refer [useTranslation]]
   [leihs.lending.client.lib.utils :refer [cj]]
   [uix.core :as uix :refer [$ defui]]))

(defui UserPopover
  "User details in a popover. Loading is injected: `use-details` is a hook
   `[user-id enabled?]` returning `{:data :ready? :error?}`. Opening the trigger
   starts the query; the popover itself only opens once the data is ready, so it
   never resizes under the cursor. A failed query shows a message in place of
   the details the list does not already carry."
  [{:keys [user name use-details test-id]}]
  (let [[t] (useTranslation)
        [requested? set-requested!] (uix/use-state false)
        {details :data ready? :ready? error? :error?} (use-details (:id user) requested?)
        suspended? (:isSuspended user)
        avatar-url (:img256Url details)
        badge-id (:badgeId details)]
    ($ Popover {:open (and requested? ready?)
                :on-open-change set-requested!}
       ($ PopoverTrigger {:data-test-id test-id}
          ($ :div {:class-name "flex items-center gap-3"}
             ($ :span {:class-name "font-semibold"} name)
             (when suspended?
               ($ UserX {:class-name "size-4 text-destructive"}))))
       ($ PopoverContent {:align "start" :class-name "w-[400px] text-sm"}
          ($ :div {:class-name "flex gap-3"}
             (if avatar-url
               ($ :div {:class-name "size-16 rounded-full overflow-hidden shrink-0"}
                  ($ :img {:src avatar-url
                           :alt name
                           :class-name "size-full object-cover scale-125"}))
               ($ :div {:class-name "size-16 rounded-full bg-muted flex items-center justify-center shrink-0"}
                  ($ UserRound {:class-name "size-8 text-muted-foreground"})))
             ($ :div {:class-name ""}
                ($ :div {:class-name "font-semibold text-base"} name)
                (if error?
                  ($ :div {:class-name "text-destructive"} (t "common.load-error"))
                  ($ :div
                     ($ :div (:email details))
                     (when badge-id
                       ($ :div {:class-name "mt-2"} (t "components.entities.user-popover.badge" (cj {:badgeId badge-id}))))
                     (when suspended?
                       ($ :p {:class-name "whitespace-pre-wrap text-destructive font-semibold mt-3"}
                          (or (:suspendedReason details)
                              (t "components.entities.user-popover.suspended"))))))))))))
