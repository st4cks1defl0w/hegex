(ns district-registry.ui.trading.events
  (:require
   [re-frame.core :as re-frame :refer [dispatch reg-event-fx]]
   [cljs-0x-connect.http-client :as http-client]))

(def interceptors [re-frame/trim-v])

(def ^:private apiclient
  (http-client/create-http-client "https://api.radarrelay.com/0x/v0/"))


(re-frame/reg-event-fx
  ::create-offer
  interceptors
  (fn [{:keys [db]} [id]]
    (println "dbg WIP creating 0x offer")))
