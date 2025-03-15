#!/usr/bin/env bb
(require '[cheshire.core :as json] '[selmer.parser :refer [render]])


(def old-routes (json/parse-string (slurp "datasets/old_routes.json") keyword))

(def new-routes (json/parse-string (slurp "datasets/new_routes.json") keyword))

(defn extract-nodes
  [v]
  (let [unique-names (set (flatten (map (comp seq :stops) v)))]
    (into {} (map vector unique-names (range)))))

(def edgeid (atom 0))
(defn create-edges
  [{:keys [route_id stops start_loc end_loc]} node-map]
  (let [nodes (map #(get node-map %) stops)
        edges (map vector nodes (drop 1 nodes))]
    (for [edge edges
          :let [eid (swap! edgeid inc)]
          :when (apply not= edge)]
      (into {:route route_id,
             :id eid,
             :route_name (format "%s-%s" start_loc end_loc)}
            (map vector [:source :target] edge)))))

(defn make-graph
  [routes title]
  (let [route-nodes (extract-nodes routes)
        route-edges (flatten (map #(create-edges % route-nodes) routes))]
    {:name title,
     :nodes (map (fn [[nname nid]] {:id nid, :name nname}) route-nodes),
     :edges route-edges}))

(defn write-gml
  [routes filename graph-title]
  (println "writing GML to" filename)
  (spit filename
        (render (slurp "scripts/graph.gml") (make-graph routes graph-title))))

(defn write-json
  [routes filename graph-title]
  (println "writing JSON to" filename)
  (spit filename (json/encode (make-graph routes graph-title))))

(write-gml old-routes "datasets/old-routes-simple-graph.gml" "Routes (2019)")
(write-gml new-routes "datasets/new-routes-simple-graph.gml" "Routes (2024)")
(write-json old-routes "datasets/old-routes-simple-graph.json" "Routes (2019)")
(write-json new-routes "datasets/new-routes-simple-graph.json" "Routes (2024)")
