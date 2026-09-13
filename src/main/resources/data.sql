

INSERT INTO customers (name, email, region, tier) VALUES

    ('Alice Uwase',   'alice.uwase@example.com',   'Kigali',   'ENTERPRISE'),
    ('Brian Mugisha', 'brian.mugisha@example.com', 'Kigali',   'PREMIUM'),
    ('Claire Ingabire','claire.ingabire@example.com','Huye',   'STANDARD'),
    ('David Niyonzima','david.niyonzima@example.com','Musanze','STANDARD'),
    ('Eva Mutesi',    'eva.mutesi@example.com',    'Rubavu',   'PREMIUM'),
    ('Felix Habimana', 'felix.habimana@example.com', 'Kigali',  'STANDARD'),
    ('Grace Umutoni',  'grace.umutoni@example.com',  'Huye',    'ENTERPRISE'),
    ('Henry Nshimiyimana','henry.nshimiyimana@example.com','Musanze','PREMIUM'),
    ('Irene Uwimana',  'irene.uwimana@example.com',  'Rubavu',  'STANDARD'),
    ('Jean Habyarimana','jean.habyarimana@example.com','Kigali','PREMIUM')

ON CONFLICT (email) DO NOTHING;

INSERT INTO products (name, sku, category, unit_price, stock_quantity) VALUES
    ('Wireless Mouse',       'ELEC-001', 'Electronics', 10000,  120),
    ('Mechanical Keyboard',  'ELEC-002', 'Electronics', 20000,   45),
    ('USB-C Hub',            'ELEC-003', 'Electronics', 30000,   15),
    ('27" Monitor',          'ELEC-004', 'Electronics', 40000,   8),
    ('Office Chair',         'FURN-001', 'Furniture',   50000,  30),
    ('Standing Desk',        'FURN-002', 'Furniture',   60000,   5),
    ('Notebook (A5)',        'STAT-001', 'Stationery',   70000,  500),
    ('Ballpoint Pen (Box)',  'STAT-002', 'Stationery',   80000,  300)
ON CONFLICT (sku) DO NOTHING;
