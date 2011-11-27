function radio_action(opt)

if opt == 1
    set(findobj('Tag','edit_cw'),'enable','off')
    set(findobj('Tag','edit_rhow'),'enable','off')
    set(findobj('Tag','editTemp'),'enable','on')
    set(findobj('Tag','editSal'),'enable','on')
    set(findobj('Tag','editDep'),'enable','on')
else
    set(findobj('Tag','edit_cw'),'enable','on')
    set(findobj('Tag','edit_rhow'),'enable','on')
    set(findobj('Tag','editTemp'),'enable','off')
    set(findobj('Tag','editSal'),'enable','off')
    set(findobj('Tag','editDep'),'enable','off')
end
return