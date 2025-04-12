#!/usr/bin/env bb

(require '[cheshire.core :as json]
         '[clojure.string :as str]
         '[babashka.http-client :as http])

(def new-routes
  (json/parse-string (slurp "datasets/new-routes-simple-graph.json") keyword))
(def old-routes
  (json/parse-string (slurp "datasets/old-routes-simple-graph.json") keyword))

(defn geocode
  [place]
  (let [headers {"Accept" "application/json",
                 "User-Agent" "matatu-routes-geocode/0.1"}
        query-params {"q" place,
                      "format" "json",
                      "countrycodes" "ke",
                      "viewbox" "36.38809,-1.05618,37.67899,-1.49003",
                      "limit" "5",
                      "bounded" "1",
                      "addressdetails" "1",
                      "email" "name@example.org"}]
    (-> (http/get "https://nominatim.openstreetmap.org/search"
                  {:headers headers, :query-params query-params})
        :body
        json/parse-string)))

(def geocode-memo (memoize geocode))

(def results
  (for [{:keys [id name]} (:nodes new-routes)]
    (do           
      (Thread/sleep 2000) ;; Nominatim only allows one request per second
      {:id id :name name :geocodes (geocode name)})))

(defn write-jsonl
  [filename ms]
  (->> ms
       (map json/encode)
       (str/join "\n")
       (spit filename)))

(write-jsonl "datasets/new_routes_geocoded.jsonl" results)

(comment
  (->> results
      (filter (comp empty? :geocodes))))
