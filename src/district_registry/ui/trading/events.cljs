(ns district-registry.ui.trading.events
  (:require
   [re-frame.core :as re-frame :refer [dispatch reg-event-fx]]
   [cljs-bean.core :refer [bean ->clj ->js]]
   [cljs-0x-connect.http-client :as http-client]))

(def interceptors [re-frame/trim-v])


;; biggest relay, no ropsten
#_(def ^:private apiclient
  (http-client/create-http-client "https://api.radarrelay.com/0x/v3/"))

(def ^:private apiclient
  (http-client/create-http-client "https://sra.bamboorelay.com/ropsten/0x/v3/"))


(re-frame/reg-event-fx
  ::create-offer
  interceptors
  (fn [{:keys [db]} [id]]
    (println "dbg WIP creating 0x offer for id" id)))



(def ^:private token-pair
  {:base-token-address "0xe41d2489571d322189246dafa5ebde1f4699f498"
   :quote-token-address "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"})

;; Uncaught (in promise) SyntaxError: JSON.parse: unexpected character at line 1 column 1 of the JSON data
#_(defn- orders-legacy []
;; get-token-pairs-async
  (js-invoke
   (http-client/get-orderbook-async
          apiclient
          #_{:request token-pair}) "then"
         (fn [r]
           (println "orders are" r))))

#_(orders)





;;TODO resolve CORS problem, outdated api endpoint?

;;NOTE repl functions
#_(orders)
