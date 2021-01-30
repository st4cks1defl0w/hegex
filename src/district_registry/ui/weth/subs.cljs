(ns district-registry.ui.weth.subs
(:require
    [district-registry.ui.config :as config]
    [district-registry.ui.contract.hegex-nft :as hegex-nft]
    [district.format :as format]
    [district.ui.web3-accounts.queries :as account-queries]
    [re-frame.core :as re-frame]))


(re-frame/reg-sub
 ::balance
  (fn [db _]
    (get-in db [:weth :balance])))
