^:kindly/hide-code
(ns index
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]))

^:kindly/hide-code (def md (comp kindly/hide-code kind/md))

(md
  "

## Introduction


Public transportation is an important amenity and is often the better choice
both in terms of affordability and limiting your [carbon footprint][1]. For this
reason, decisions impacting the transportation network need to be not just well
intentioned, but well thought out too.

The Government of Kenya in [December 2024][2] proposed changes to existing bus routes 
in Nairobi aimed at reducing congestion around the CBD (Central Business District)
and lowering transit times around the city.

Our goal is to compare the existing and new routes across a variety of factors. More
specifically:

- Are the new routes centralized or decentralized?
- How many different transit hubs does each version have and where are they?
- How do the changes impact traffic congestion?
- Is it easier or harder to move around the city via bus?

To answer the first two questions we'll transform the routes into simplified graphs. By
comparing verious metrics on the two graphs we can begin to understand what the answers
may be.

The later two questions will be answered by simulating commuters travelling around the city
making use of the transportation network.

[1]: https://ourworldindata.org/travel-carbon-footprint#walk-bike-or-take-the-train-for-the-lowest-footprint
[2]: https://new.kenyalaw.org/akn/ke/officialGazette/2024-12-20/226/eng@2024-12-20#page-36
")

(md "

## Setup 

")

(ns index
  (:require [cheshire.core :as json]
            [clojure.set :as s]
            [scicloj.tableplot.v1.plotly :as plotly]
            [tablecloth.api :as tc]
            [scicloj.kindly.v4.kind :as kind])
  (:import
    (org.jgrapht.graph DefaultUndirectedWeightedGraph DefaultWeightedEdge)
    (org.jgrapht.alg.scoring BetweennessCentrality ClusteringCoefficient)))



;; ## Loading Data

(def new-routes
  (json/decode (slurp "datasets/new-routes-simple-graph.json") keyword))
(def old-routes
  (json/decode (slurp "datasets/old-routes-simple-graph.json") keyword))


(md
  "
##  Bulding the Graph

We use the Java library [JGraphT] to build a graph representation of the
two route versions. The vertices of the graph are the individual stops. The
existence of an edge means that at least one route exists that connects
stops. The edge weight is used to keep track of how many different routes
service two stops. A larger weight means that more routes use the connected
stops as transit points.

")

(defn lookup
  [vecmap lmap]
  (filter (fn [x] (every? true? (map (merge-with = lmap x) (keys lmap))))
    vecmap))

(defn build-graph
  [routes]
  (let [g (DefaultUndirectedWeightedGraph. DefaultWeightedEdge)
        nodes (:nodes routes)
        edges (:edges routes)]
    (doseq [{:keys [id]} nodes] (.addVertex g id))
    (doseq [{:keys [source target]} edges]
      (when-let [e (.addEdge g source target)]
        (.setEdgeWeight g
                        e
                        (count (lookup edges
                                       {:source source, :target target})))))
    g))

(def old-graph (build-graph old-routes))
(def new-graph (build-graph new-routes))


(md
  "
## Defining Metrics

We define a few convenience funtions for computing various metrics. In particular:

- `:degree` - how many edges a particular vertex is connected to.
- `:edge-weight-sum` - the sum of all the weights for connecting edges.
- `:betweeness` - measures of how important a vertex is to paths in the graph
- `:clustering-coefficient` - measures how closely vertices group together
")

(defn degree
  [graph]
  (->> (.vertexSet graph)
       (map (fn [v] {:id v, :degree (.degreeOf graph v)}))))

(defn edge-weight-sum
  [graph]
  (for [vertex (.vertexSet graph)]
    {:id vertex,
     :edge-weight-sum
       (reduce + (map #(.getEdgeWeight graph %) (.edgesOf graph vertex)))}))

(defn betweenness-centrality
  [graph]
  (-> graph
      (BetweennessCentrality. true)
      .getScores
      (->> (into {})
           (map (fn [[k v]] {:id k, :betweenness v})))))

(defn clustering-coefficient
  [graph]
  (-> graph
      (ClusteringCoefficient.)
      .getScores
      (->> (into {})
           (map (fn [[k v]] {:id k, :clustering-coefficient v})))))


(defn assemble-metrics
  [graph fns]
  (->> graph
       ((apply juxt fns))
       (reduce s/join)))

(def new-route-metrics
  (let [fns [degree betweenness-centrality clustering-coefficient
             edge-weight-sum]
        metrics (map #(assoc % :graph :new) (assemble-metrics new-graph fns))]
    metrics))

(def old-route-metrics
  (let [fns [degree betweenness-centrality clustering-coefficient
             edge-weight-sum]
        metrics (map #(assoc % :graph :old) (assemble-metrics old-graph fns))]
    metrics))


(def metrics-dataset
  (-> (flatten [new-route-metrics old-route-metrics])
      tc/dataset
      (tc/set-dataset-name "Route Graph Metrics")))

metrics-dataset

(md
 "
## Comparing The Graphs

The `:betweeness` and `:degree` show that the new routes do indeed spread out transit
across more vertices in the graph which translates to a more decentralized network.
Decentralization is a desirable property for a few reasons:

- More than one way to travel between points means that you can always get anywhere even if an
  agency servicing a particular route suspends service or some road is closed.
- Less road congestion overall since traffic is spread out over multiple locations.

However, there are also downsides including:

- Making it harder to find the right bus to take.
- Transport costs may increase especially if rates are not standardized across all routes.

In order for a change to be justifiable, the pros need to significantly outweigh the cons and also
be worth the effort required by the various stakeholders to adapt to the change.
")

(-> metrics-dataset
    (plotly/base {:=x :degree,
                  :=y :betweenness,
                  :=color :graph,
                  :=title
                    "Betweenness Centrality and Degree Across Route Versions",
                  :=color-type :nominal,
                  :=mark-opacity 0.5})
    plotly/layer-point
    (plotly/layer-smooth {:=dataset (tc/order-by metrics-dataset
                                                 [:graph :degree :betweeness]),
                          :=mark-size 2}))


(md
  "

From the distribution of the two graphs' clustering coefficients we can
see that the new routes cluster around different points which would in
theory spread out the load across multiple nodes. In contrast the old routes
rely on a centralized hub(s) for transit between points.

")

(-> metrics-dataset
    (plotly/layer-histogram {:=x :clustering-coefficient,
                             :=histogram-nbins 25,
                             :=title "Clustering Coefficient Distribution",
                             :=color :graph,
                             :=color-type :nominal,
                             :=mark-opacity 0.6}))


(md
  "

On the other hand the `:edge-weight-sum` shows that although the routes are distributed, the load 
on each node isn't as spread out as would be ideal given that multiple routes pass through the same nodes.
What this means that instead of completely eliminating the bottleneck of using a single transit hub,
the new routes create slightly smaller bottlenecks in multiple places.
")

(-> metrics-dataset
    (plotly/layer-histogram {:=x :edge-weight-sum,
                             :=histogram-nbins 5,
                             :=color :graph,
                             :=title "Edge Weight Sum Distribution",
                             :=color-type :nominal,
                             :=mark-opacity 0.6}))

(md "
### Graph of Old Routes
") 


(defn find-by [xs k v] (first (filter (comp #(= v %) k) xs)))

(defn make-echarts
  [routes graph metrics title]
  {:title {:text title},
   :tooltip {},
   :legend [],
   :series [{:name ,
             :type "graph",
             :layout "force",
             :emphasis {:focus "adjacency", :lineStyle {:width 10}},
             :data (map (fn [v]
                          (let [vmetrics (find-by metrics :id v)
                                vinfo (find-by (:nodes routes) :id v)]
                            (reduce into
                              [vmetrics vinfo
                               {:symbolSize (* 5 (:degree vmetrics))}])))
                     (.vertexSet graph)),
             :links (map (fn [e]
                           {:id (str (.getEdgeSource graph e)
                                     (.getEdgeTarget graph e)),
                            :source (.getEdgeSource graph e),
                            :target (.getEdgeTarget graph e),
                            :weight (.getEdgeWeight graph e)})
                      (.edgeSet graph)),
             :roam false,
             :label {:position "right"},
             :lineStyle {:width 3},
             :force {:repulsion 100}}]})
(kind/echarts
  (make-echarts old-routes old-graph old-route-metrics "Old Routes Graph"))
#_(kind/echarts
    (make-echarts new-routes new-graph new-route-metrics "New Routes"))

(md
 "
## Simulation

Still under development...

- [SUMO](https://sumo.dlr.de/docs/Tutorials/PT_from_OpenStreetMap.html)
- [Agents.jl](https://juliadynamics.github.io/Agents.jl/stable/examples/schoolyard/)
")
