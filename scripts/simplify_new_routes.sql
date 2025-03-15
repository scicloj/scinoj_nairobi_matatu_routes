-- Produces simplified graph from new routes
-- run with duckdb < scripts/simplify_new_routes.sql
copy
  (with grouped_stops as
     (select array_agg(stop_name) as stops,
             route_id
      from read_csv('datasets/new_stops.txt')
      group by route_id) select gs.route_id,
                                gs.stops,
                                new_routes."Start" as start_loc,
                                new_routes."End" as end_loc
   from grouped_stops gs
   inner join
     (select *
      from read_json('datasets/raw/routes_2024_gazette/routes.json')) new_routes
  on new_routes."Route No" = gs.route_id
)
to 'datasets/new_routes.json' (format json, array true);
