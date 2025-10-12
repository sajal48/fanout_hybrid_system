#!/bin/bash
# Copy custom pg_hba.conf after database initialization
cp /etc/postgresql/pg_hba.conf $PGDATA/pg_hba.conf
chmod 600 $PGDATA/pg_hba.conf
chown postgres:postgres $PGDATA/pg_hba.conf
echo "Custom pg_hba.conf applied with trust authentication"
