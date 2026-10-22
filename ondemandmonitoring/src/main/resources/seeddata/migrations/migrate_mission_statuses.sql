UPDATE public.missions
SET status = 'PLANNING'
WHERE status IN ('CREATED', 'RESOURCE_ASSIGNING');
