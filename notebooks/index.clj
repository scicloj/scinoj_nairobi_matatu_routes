(ns index
  (:require [scicloj.kindly.v4.kind :as kind]
            [clojure.string :as str]
            [tablecloth.api :as tc]
            [scicloj.tableplot.v1.plotly :as plotly]))



;; load distinct colors for use in coloring route edges
;; generated with `pastel distinct 500 | pastel format hex > datasets/colors.txt`
(def colors (str/split-lines (slurp "datasets/colors.txt")))



;; read old routes from json
(def old-routes
  (-> (tc/dataset "datasets/old_routes.json" {:key-fn keyword})
      (tc/set-dataset-name "Old Routes")
      #_(tc/head 30)
      (tc/rows :as-maps)))

;; same as above but for new routes
(def new-routes
  (-> (tc/dataset "datasets/new_routes.json" {:key-fn keyword})
      (tc/set-dataset-name "New Routes")
      #_(tc/head 30)
      (tc/rows :as-maps)))

;; uses `route_id` as categories to partition graph
(defn get-categories [v] (map (fn [x] {:name (:route_id x)}) v))

;; find all nodes and add them to a map with an int identifier. This is
;; necessary because echarts does not allow for duplicate nodes in the graph chart.
(defn get-node-ids
  [v]
  (let [unique-names (set (flatten (map (comp seq :stops) v)))]
    (into {} (map vector unique-names (range)))))

;; create edges based on the assumption that stops are connected linearly e.g with stops
;; [a, b , c] we assume that a is connected to b and b is connected to c and so on. An atom is
;; used to make sure the edge id are globally unique. There's probably a better way to do this
(def edgeid (atom 0))
(defn build-edges
  [route node-ids color-map]
  (let [nodes (map str (map #(get node-ids %) (:stops route)))
        edges (map vector nodes (drop 1 nodes))]
    (for [edge edges
          :let [eid (str (swap! edgeid inc))]]
      (into {:id eid, :lineStyle {:color (get color-map (:route_id route))}}
            (map vector [:source :target] edge)))))

;; produce echarts compatible data map for visualization
(defn routes->graph
  [routes]
  (let [categories (get-categories routes)
        node-ids (get-node-ids routes)
        color-map (into {} (map vector (map :route_id routes) colors))
        edges (flatten (map #(build-edges % node-ids color-map) routes))
        nodes (for [[pname pid] node-ids]
                {:symbolSize 10, :name pname, :id (str pid)})]
    {:categories categories, :nodes nodes, :links edges}))

(def old-routes-graph (routes->graph old-routes))
(def new-routes-graph (routes->graph new-routes))

;; echarts chart description
(def old-routes-chart
  {:title {:text "Old Routes Graph"},
   :tooltip {},
   :legend [],
   :series [{:name "Old Routes",
             :type "graph",
             :layout "force",
             :data (:nodes old-routes-graph),
             :links (:links old-routes-graph),
             :categories (:categories old-routes-graph),
             :roam false,
             :zoom 0.5,
             :lineStyle {:width 3},
             :force {:repulsion 100}}]})

(kind/echarts old-routes-chart)

(def new-routes-chart
  {:title {:text "New Routes Graph"},
   :tooltip {},
   :legend [],
   :series [{:name "New Routes",
             :type "graph",
             :layout "force",
             :data (:nodes new-routes-graph),
             :links (:links new-routes-graph),
             :categories (:categories new-routes-graph),
             :roam false,
             :zoom 0.5,
             :lineStyle {:width 3},
             :force {:repulsion 500}}]})
#_(spit "chart.json" (json/encode old-routes-chart))
#_(kind/echarts new-routes-chart)
