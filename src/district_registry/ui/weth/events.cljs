(ns district-registry.ui.weth.events
  (:require
   [re-frame.core :as re-frame :refer [dispatch reg-event-fx]]
    [district.ui.web3-accounts.queries :as account-queries]
    [district.ui.web3-tx.events :as tx-events]
    [district.format :as format]
    [district.ui.logging.events :as logging]
    [district.ui.smart-contracts.queries :as contract-queries]
    [district.ui.web3.queries :as web3-queries]
   [web3 :as web3webpack]
    [district.web3-utils :as web3-utils]
    [oops.core :refer [oget oset! ocall oapply ocall! oapply!
                       gget
                       oget+ oset!+ ocall+ oapply+ ocall!+ oapply!+]]
   [cljs.core.async :refer [go]]
   [bignumber.core :as bn]
   [web3 :as web3+]
   [cljs.core.async.interop :refer-macros [<p!]]
   [cljs-bean.core :refer [bean ->clj ->js]]
   ["@0x/connect" :as connect0x]
   ["@0x/web3-wrapper" :as web3-wrapper]
   ["@0x/contract-wrappers" :as contract-wrappers]
   ["@0x/contract-addresses" :as contract-addresses]
   ["@0x/utils" :as utils0x]
   ["@0x/order-utils" :as order-utils0x]
   ["@0x/subproviders" :as subproviders0x]
   [cljs-0x-connect.http-client :as http-client]))

(def interceptors [re-frame/trim-v])

(re-frame/reg-event-fx
  ::weth-balance
  interceptors
  (fn [{:keys [db]} _]
    {:web3/call
     {:web3 (web3-queries/web3 db)
      :fns [{:instance (contract-queries/instance db :weth)
             :fn :balanceOf
             :args [(account-queries/active-account db)]
             :on-success [::weth-balance-success]
             :on-error [::logging/error [::weth-balance]]}]}}))


(re-frame/reg-event-fx
  ::weth-balance-success
  interceptors
  (fn [{:keys [db]} [balance]]
    {:db (assoc-in db [:weth :balance]
                   (some-> balance
                           bn/number
                           web3-utils/wei->eth-number
                           (format/format-number {:max-fraction-digits 5})))}))


(re-frame/reg-event-fx
  ::wrap
  interceptors
  (fn [{:keys [db]} [form]]
    (let [weis (some-> (:weth/amount form) web3-utils/eth->wei-number)]
      {:dispatch [::tx-events/send-tx
                 {:instance (contract-queries/instance db :weth)
                  :fn :deposit
                  :args []
                  :tx-opts {:value weis
                            :from (account-queries/active-account db)}
                  :tx-id :wrap-eth
                  :on-tx-success [::wrap-success]
                  :on-tx-error [::logging/error [::wrap]]}]})))

(re-frame/reg-event-fx
  ::wrap-success
  interceptors
  (fn [_ _]
    {:dispatch [::weth-balance]}))
