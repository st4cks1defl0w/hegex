(ns district-registry.ui.contract.hegex-nft
  (:require
   [bignumber.core :as bn]
   [district.ui.smart-contracts.subs :as contracts-subs]
   [re-frame.core :refer [subscribe dispatch]]
   [cljs-web3-next.eth :as web3-ethn]
   [cljs.core.async :refer [go]]
   [cljs.core.async.interop :refer-macros [<p!]]
   [web3 :as web3js]
   #_[react :refer [createElement]]
  #_ ["react-dom/server" :as ReactDOMServer :refer [renderToString]]
   [cljs-bean.core :refer [bean ->clj ->js]]
    [cljs-web3.core :as web3]
    [oops.core :refer [oget oset! ocall oapply ocall! oapply!
                       gget
                       oget+ oset!+ ocall+ oapply+ ocall!+ oapply!+]]
    [district-registry.shared.utils :refer [debounce]]
    [cljs-web3.eth :as web3-eth]
    [cljs.spec.alpha :as s]
    [district.format :as format]
    [district.parsers :as parsers]
    [district.ui.logging.events :as logging]
    [district.ui.notification.events :as notification-events]
    [district.ui.smart-contracts.queries :as contract-queries]
    [district.ui.web3-accounts.queries :as account-queries]
    [district.ui.web3-tx.events :as tx-events]
    [district.ui.web3.queries :as web3-queries]
    [district.web3-utils :as web3-utils]
    [goog.string :as gstring]
    [print.foo :refer [look] :include-macros true]
    [re-frame.core :as re-frame :refer [dispatch reg-event-fx]]))

(def interceptors [re-frame/trim-v])

(re-frame/reg-event-fx
  ::owner
  interceptors
  (fn [{:keys [db]} _]
    {:web3/call
     {:web3 (web3-queries/web3 db)
      :fns [{:instance (contract-queries/instance db :hegexoption)
             :fn :owner
             :on-success [::owner-success]
             :on-error [::logging/error [::owner]]}]}}))


(re-frame/reg-event-fx
  ::owner-success
  interceptors
  (fn [{:keys [db]} [owner]]
    (println "dbg" "owner is" owner)
    {:db (assoc-in db [::owner] owner)}))


(re-frame/reg-event-fx
  ::hegic-options
  interceptors
  (fn [{:keys [db]} [opt-ids]]
    {:db (assoc-in db [::hegic-options] opt-ids)}))

(def deb-owner
  (debounce
   (fn []
     (dispatch [::owner]))
    500))

#_(defn get-event [web3-host]
  (let [Web3 (gget "Web3")
        _ (js/console.log web3-host)
        _ (println "pre_____" (gget "web3.?version"))
        web3js (Web3. (gget ".?web3.?currentProvider"))
        _  (oset! js/window "web3" web3js)
        _ (ocall! js/window.ethereum "enable")
        past-logs {
                   ;; :fromBlock "0"
                   ;; :toBlock   "latest"
                   :address "0x1f12132e83bec1431221023f85c964305df535d7"
                   :topics ["0x8be0079c531659141344cd1fd0a4f28419497f9722a3daafe3b4186f6b6457e0"]}
        get-past-logs (oget web3js ".?eth.?getPastLogs")]
    (println "_____" (gget "web3.?version"))
    #_(println (get-past-logs past-logs #_(clj->js past-logs)))))

;; web3.eth.getPastLogs({
;;     address: "0x11f4d0A3c12e86B4b5F39B213F7E19D048276DAe",
;;     topics: ["0x033456732123ffff2342342dd12342434324234234fd234fd23fd4f23d4234"]
;; })

    ;; window.web3 = new Web3(window.web3.currentProvider)
    ;; window.ethereum.enable();


(defn get-event [web3-host]
  (let [Web3 (gget "Web3")
        web3js (Web3. (gget ".?web3.?currentProvider"))
        _  (oset! js/window "web3" web3js)
        _ (ocall! js/window.ethereum "enable")]
    ;; TODO
    ;; test with inferred externs or migrate to oops
    ;; NOTE
    ;; those are for ropsten
    (.then (js/web3.eth.getPastLogs
            (clj->js {:address "0x77041D13e0B9587e0062239d083b51cB6d81404D"
                      :topics ["0x9acccf962da4ed9c3db3a1beedb70b0d4c3f6a69c170baca7198a74548b5ef4e", nil, "0x000000000000000000000000b95fe51930ddfc546ff766d59288b50170244b4a"]
                      :fromBlock 0
                      :toBlock "latest"}))

           (fn [ev]
             (dispatch [::hegic-options (-> ev first bean :topics second)])
             (println "my option is" (-> ev first bean :topics second))))
    #_(web3-ethn/get-past-logs (gget "web3")
                             {:address "0xEfC0eEAdC1132A12c9487d800112693bf49EcfA2"
                              :topics [["0x5f36a4a575e512eb69d6d28c3b0ff98cca7ba50ad5bf04e14094ad1d425e0d31", "0x00000000000000000000000000000000000000000000000000000000000005e1"]]
                              :fromBlock 0
                              :toBlock "latest"}
                              #_c
                              #_:OwnershipTransferred
                              #_{:from-block 0
                               :to-block "latest"}
                              (fn [events]
                                (println "events are" events)))))


;;successful loq query on mainnet
;;query of Expire event
;; web3.eth.getPastLogs({
;;     address: "0xEfC0eEAdC1132A12c9487d800112693bf49EcfA2",
;;     topics: [["0x5f36a4a575e512eb69d6d28c3b0ff98cca7ba50ad5bf04e14094ad1d425e0d31", "0x00000000000000000000000000000000000000000000000000000000000005e1"]], fromBlock: 0, toBlock: "latest"
;; })
;; .then(console.log);
