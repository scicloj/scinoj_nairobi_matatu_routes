graph [
  directed 0
  label "{{name}}"

  {% for node in nodes %}
  node [
    id {{ node.id }}
    label "{{ node.name }}"
    name "{{ node.name }}"
  ]
  {% endfor %}

  {% for edge in edges %}
  edge [
    source {{ edge.source }}
    target {{ edge.target }}
    id  {{ edge.id }}
    route_id "{{ edge.route }}"
    route_name "{{ edge.route_name }}"
  ]
  {% endfor %}
]
