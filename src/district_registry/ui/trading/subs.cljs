(ns district-registry.ui.trading.subs
  (:require
   [cljs-0x-connect.http-client :as http-client]))


(def ^:private apiclient
  (http-client/create-http-client "https://api.radarrelay.com/0x/v0/"))

(defn- get-orderbook []
  (.then (http-client/get-orders-async apiclient)
         (fn [data]
           (println "dbg data")

           )))
