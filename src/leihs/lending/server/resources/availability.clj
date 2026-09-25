(ns leihs.lending.server.resources.availability
  (:require
   [leihs.core.availability.changes :as ch]))

(defn- group->row
  [[group-id {:keys [in-quantity running-reservations]}]]
  {:entitlement_group_id (when-not (= group-id :general) group-id)
   :in_quantity in-quantity
   :reservation_ids running-reservations})

(defn get-multiple
  "Raw availability changes of the model in the pool, sorted by date, starting
  today. Each change holds until the next one. General group comes first, the
  others follow sorted by id."
  [{{tx :tx pool-id :pool-id} :request} _ {model-id :id}]
  (->> (ch/main tx model-id pool-id)
       (sort-by key)
       (map (fn [[date groups]]
              {:date date
               :groups (->> groups
                            (sort-by (fn [[group-id _]]
                                       (when-not (= group-id :general) (str group-id))))
                            (map group->row))}))))
