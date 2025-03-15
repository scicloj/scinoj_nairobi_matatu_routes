#!/usr/bin/env bb
(require '[cheshire.core :as json] '[clojure.string :as str])

;; produces a list of stops for manual cleaning

(def raw-json
  (json/parse-string (slurp "datasets/raw/routes_2024_gazette/routes.json")))

(def process-key
  (comp keyword str/trim #(str/replace % #"\s+" "_") str/lower-case))

(def processed-data (map #(update-keys % process-key) raw-json))

(defn process-stage
  [stages]
  (-> stages
      str/lower-case
      (str/replace #"\s+" " ")
      (str/replace #"\s\(\s*.+\s*\)\s*" "")
      (str/split #"\s*,\s*")))


(defn group-places
  [m]
  (let [{:keys [route_no key_stages]} m
        route_no (repeat route_no)]
    (map vector key_stages route_no)))


(->> processed-data
       (map #(update % :key_stages process-stage))
       (map group-places)
       (into [] cat)
       (sort-by first)
       (map #(apply format "%s,%s" %))
       (into ["stop_name,route_id"])
       (str/join "\n")
       (spit "datasets/new_stops.txt"))
