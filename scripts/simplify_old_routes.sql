-- Produces simplified graph from old routes
-- run with duckdb < scripts/simplify_old_routes.sql
copy
(select
      route_id,
      regexp_split_to_array(
          route_long_name.trim().lower().regexp_replace('\s+', ' ', 'g'), '-'
      ) as stops,
      regexp_split_to_array(routes.route_long_name, '-')[1]
        .trim()
        .lower()
        .regexp_replace('\s+', ' ', 'g') as start_loc,
      regexp_split_to_array(routes.route_long_name, '-')[-1]
        .trim()
        .lower()
        .regexp_replace('\s+', ' ', 'g') as end_loc
  from read_csv('datasets/raw/routes_2019_gtfs/routes.txt') routes
)
to 'datasets/old_routes.json' (format json, array true);
