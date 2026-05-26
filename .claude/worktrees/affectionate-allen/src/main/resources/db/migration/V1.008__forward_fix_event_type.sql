-- Forward fix to correct ‚Äì in event_type display_names

UPDATE event_type SET display_name = 'Custody Warrant on Extradition - Category 2 Territory'
    WHERE id = 17;

UPDATE event_type SET display_name = 'Custody Warrant on Extradition (with Bail Direction) - Category 2 Territory'
    WHERE id = 21;

UPDATE event_type SET display_name = 'Custody Warrant Sending to Secretary of State - Category 2 Territory'
    WHERE id = 22;

UPDATE event_type SET display_name = 'Custody Warrant Sending to Secretary of State on Consent - Category 2 Territory'
    WHERE id = 23;

UPDATE event_type SET display_name = 'Custody Warrant with Bail Direction Sending to Secretary of State - Category 2 Territory'
    WHERE id = 26;

UPDATE event_type SET display_name = 'Custody Warrant with Bail Direction Sending to Secretary of State on Consent - Category 2 Territory'
    WHERE id = 27;