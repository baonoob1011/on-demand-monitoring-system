# Forest3D IDEA Workflow

Tu nay sua code Forest3D truc tiep trong IntelliJ IDEA tai:

```text
C:\Users\ACER\Documents\GitHub\doan\on-demand-monitoring-system\Forest3D
```

Ban khong can mo Ubuntu de sua file nua.

## File quan trong

- `worlds/forest_monitoring.sdf`: Gazebo world cho demo capstone.
- `tools/generate_capstone_world.py`: script tao lai world neu can chinh layout.
- `README.md`: lenh chay Gazebo va PX4.

## Chay Gazebo/PX4

Gazebo va PX4 van dang cai trong WSL Ubuntu, nen khi can chay simulator tu Windows/IDEA terminal, dung:

```powershell
.\Forest3D\run_forest_monitoring.ps1
```

Neu muon generate lai world:

```powershell
wsl.exe -d Ubuntu-24.04 -- /bin/sh -lc "cd /mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/Forest3D && python3 tools/generate_capstone_world.py"
```

Ban chi can vao Ubuntu truc tiep khi muon debug moi truong PX4/Gazebo nang hon.

## Kiem tra dang chay dung map

Trong Gazebo Entity Tree, map moi phai co cac entity nhu:

- `drone_landing_pad`
- `control_cabin`
- `trail_base`
- `communication_mast`
- `burnt_ground_patch`

Neu Entity Tree van hien `grasspatch`, `grasspatch_0`, `Pine Tree_11`,
`Pine Tree_12`..., ban dang mo nham world cu cua PX4, khong phai
`Forest3D/worlds/forest_monitoring.sdf`.
