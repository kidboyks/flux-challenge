(ns ^:figwheel-always jelz.core
  (:require-macros
    [reagent.ratom :refer [reaction]])
  (:require
    [jelz.view :refer [layout]]
    [jelz.client :as client]
    [jelz.helpers :as h]
    [reagent.core :as reagent]
    [re-frame.core :refer [register-handler register-sub dispatch]]))

;;-- Handlers ------------------------------------------------------------------

(register-handler
  :planet-change
  (fn [db [_ planet]]
    (dispatch [:danger-check])
    (assoc db :planet planet)))

(register-handler
  :jedi-reset
  (fn [db _]
    (dispatch [:danger-check])
    (assoc db :slots (h/get-empty-slots))))

(register-handler
  :jedi-request-first
  (fn [db _]
    (dispatch [:jedi-request (-> db :slots first :req-id) 3616])
    db))

(register-handler
  :jedi-request
  (fn [db [_ req-id id]]
    (client/fetch-dark-jedi req-id id)
    db))

(register-handler
  :jedi-request-next
  (fn [db _]
    (let [slots      (:slots db)
          master     (h/find-master-to-load slots)
          apprentice (h/find-apprentice-to-load slots)
          to-load    (or master apprentice)]
      (when (and to-load (not (:xhr to-load)) (not (:danger db)))
        (dispatch [:jedi-request (:req-id to-load) (:id to-load)]))
      db)))

(register-handler
  :jedi-loading
  (fn [db [_ req-id xhr]]
    (let [ext {:xhr xhr :pending? true}]
      (update-in db [:slots] #(h/extend-slot % req-id ext)))))

(register-handler
  :jedi-loaded
  (fn [db [_ req-id jedi]]
    (let [ext (merge jedi {:pending? false})]
      (dispatch [:danger-check])
      (update-in db [:slots] #(h/extend-slot % req-id ext)))))

(register-handler
  :scroll
  (fn [db [_ dir]]
    (dispatch [:jedi-request-next])
    (update-in db [:slots] #(-> % h/cancel-pending (h/scroll dir)))))

(register-handler
  :danger-check
  (fn [db _]
    (let [slots         (:slots db)
          planet-id     (get-in db [:planet :id])
          planets-eq    #(= (get-in % [:homeworld :id]) planet-id)
          in-danger?    (some planets-eq slots)
          updated-slots (if in-danger? (h/cancel-pending slots) slots)]
      (when-not in-danger? (dispatch [:jedi-request-next]))
      (merge db {:danger (if in-danger? planet-id false)
                 :slots  updated-slots}))))


;;-- Subscribers ---------------------------------------------------------------

(register-sub
  :initialized?
  (fn [db _]
    (reaction (and (:planet @db) (:slots @db)))))

(register-sub :planet (fn [db _] (reaction (:planet @db))))
(register-sub :slots (fn [db _] (reaction (:slots @db))))
(register-sub :danger (fn [db _] (reaction (:danger @db))))

(register-sub
  :enabled-buttons
  (fn [db _]
    (reaction
      (let [slots      (:slots @db)
            danger     (:danger @db)
            apprentice (h/find-apprentice-to-load slots true)
            master     (h/find-master-to-load slots true)]
        {:down (and (not danger) (h/can-scroll-down? slots) (:id apprentice))
         :up   (and (not danger) (h/can-scroll-up? slots) (:id master))}))))


;;-- Run app! ------------------------------------------------------------------

(enable-console-print!)
(client/setup-planet-listener)
(dispatch [:jedi-reset])
(dispatch [:jedi-request-first])
(reagent/render [layout] (js/document.getElementById "app"))
