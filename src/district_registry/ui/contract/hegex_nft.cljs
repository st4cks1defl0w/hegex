(ns district-registry.ui.contract.hegex-nft
  (:require
   [bignumber.core :as bn]
   [cljs-bean.core :refer [bean ->clj ->js]]
    [cljs-web3.core :as web3]
    [oops.core :refer [oget oset! ocall oapply ocall! oapply!
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


(def deb-owner
  (debounce
   (fn []
     (dispatch [::owner]))
    500))

#_(defn get-event-dev []
  (web3-eth/contract-get-data
   (contract-queries/instance db :district-factory)
   :create-district
   active-account
   Hash
   aragon-id))

(defn get-event [web3-host]
  (js/console.log js/window.ethereum))
