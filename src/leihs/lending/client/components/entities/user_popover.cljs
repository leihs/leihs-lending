(ns leihs.lending.client.components.entities.user-popover
  (:require
   ["@@/popover" :refer [Popover PopoverContent PopoverTrigger]]
   ["lucide-react" :refer [UserRound UserX]]
   ["react-i18next" :refer [useTranslation]]
   ["urql" :refer [useQuery]]
   [leihs.lending.client.lib.utils :refer [cj jc]]
   [uix.core :as uix :refer [$ defui]]))

(def user-query
  "query ($id: UUID!) {
     user(id: $id) {
       email
       badgeId
       img256Url
       suspendedReason
     }
   }")

(defui UserPopover [{:keys [user name test-id]}]
  (let [[t] (useTranslation)
        suspended? (:isSuspended user)
        [open? set-open!] (uix/use-state false)
        [result] (useQuery (cj {:query user-query
                                :variables {:id (:id user)}
                                :pause (not open?)}))
        details (some-> (.-data result) jc :user)
        avatar-url (:img256Url details)
        badge-id (:badgeId details)]
    ($ Popover {:open open?
                :on-open-change set-open!}
       ($ PopoverTrigger {:data-test-id test-id}
          ($ :div {:class-name "flex items-center gap-3"}
             ($ :span {:class-name "font-semibold"} name)
             (when suspended?
               ($ UserX {:class-name "size-4 text-destructive"}))))
       ($ PopoverContent {:align "start" :class-name "w-[400px]"}
          ($ :div {:class-name "flex gap-3"}
             (if avatar-url
               ($ :div {:class-name "size-12 rounded-full overflow-hidden shrink-0"}
                  ($ :img {:src avatar-url
                           :alt name
                           :class-name "size-full object-cover scale-125"}))
               ($ :div {:class-name "size-12 rounded-full bg-muted flex items-center justify-center shrink-0"}
                  ($ UserRound {:class-name "size-6 text-muted-foreground"})))
             ($ :div
                ($ :div {:class-name "font-semibold"} name)
                ($ :div (:email details))
                (when badge-id
                  ($ :div (t "user.badge" (cj {:badgeId badge-id}))))))
          (when suspended?
            ($ :p {:class-name "whitespace-pre-wrap text-sm text-destructive mt-3"}
               (or (:suspendedReason details)
                   (t "user.suspended"))))))))
