(ns district-registry.ui.trading.subs
  (:require
   #_[cljs-0x-connect.http-client :as http-client]))


#_(def ^:private apiclient
  (http-client/create-http-client "https://api.radarrelay.com/0x/v0/"))

#_(defn- get-orderbook []
  (.then (http-client/get-orders-async apiclient)
         (fn [data]
           (println "dbg data")

           )))
