-- Seed fixed article taxonomy categories used by guided utility and subscriptions workflows.
-- Tenant-managed subcategories may extend only these categories.

INSERT INTO categories (id, name, description, created_at, updated_at)
SELECT gen_random_uuid(), v.name, v.description, NOW(), NOW()
FROM (VALUES
  ('Utilities', 'Utility bills and services such as electricity, water, gas, and heating.'),
  ('Subscriptions', 'Subscription and recurring services such as mobile, internet, hosting, AI tools, memberships, and donations.')
) AS v(name, description)
WHERE NOT EXISTS (
  SELECT 1 FROM categories c WHERE c.name = v.name
);

WITH defaults(category_name, subcategory_name, description) AS (
  VALUES
    ('Utilities', 'Electricity', 'Electricity bills and power usage.'),
    ('Utilities', 'Water', 'Water supply and related utility costs.'),
    ('Utilities', 'Gas', 'Gas utility bills.'),
    ('Utilities', 'Heating', 'Heating and district heating bills.'),
    ('Subscriptions', 'Mobile', 'Mobile phone plans and mobile services.'),
    ('Subscriptions', 'Internet', 'Internet service subscriptions.'),
    ('Subscriptions', 'Hosting', 'Web hosting, domains, and infrastructure subscriptions.'),
    ('Subscriptions', 'AI tools', 'AI assistants and AI service subscriptions.'),
    ('Subscriptions', 'Software', 'Software-as-a-service and app subscriptions.'),
    ('Subscriptions', 'Memberships', 'Membership and club subscriptions.'),
    ('Subscriptions', 'Donations', 'Recurring donations and supporter payments.')
)
INSERT INTO subcategories (
  id,
  category_id,
  tenant_id,
  name,
  description,
  is_active,
  is_system_default,
  created_at,
  updated_at
)
SELECT
  gen_random_uuid(),
  c.id,
  NULL,
  d.subcategory_name,
  d.description,
  TRUE,
  TRUE,
  NOW(),
  NOW()
FROM defaults d
JOIN categories c ON c.name = d.category_name
WHERE NOT EXISTS (
  SELECT 1
  FROM subcategories s
  WHERE s.category_id = c.id
    AND s.tenant_id IS NULL
    AND s.name = d.subcategory_name
);
