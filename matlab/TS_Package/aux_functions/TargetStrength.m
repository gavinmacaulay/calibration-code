function varargout = TargetStrength(varargin)
% TARGETSTRENGTH M-file for TargetStrength.fig
%      TARGETSTRENGTH, by itself, creates a new TARGETSTRENGTH or raises the existing
%      singleton*.
%
%      H = TARGETSTRENGTH returns the handle to a new TARGETSTRENGTH or the handle to
%      the existing singleton*.
%
%      TARGETSTRENGTH('CALLBACK',hObject,eventData,handles,...) calls the local
%      function named CALLBACK in TARGETSTRENGTH.M with the given input arguments.
%
%      TARGETSTRENGTH('Property','Value',...) creates a new TARGETSTRENGTH or raises the
%      existing singleton*.  Starting from the left, property value pairs are
%      applied to the GUI before TargetStrength_OpeningFcn gets called.  An
%      unrecognized property name or invalid value makes property application
%      stop.  All inputs are passed to TargetStrength_OpeningFcn via varargin.
%
%      *See GUI Options on GUIDE's Tools menu.  Choose "GUI allows only one
%      instance to run (singleton)".
%
% See also: GUIDE, GUIDATA, GUIHANDLES

% Edit the above text to modify the response to help TargetStrength

% Last Modified by GUIDE v2.5 22-Nov-2011 10:28:55

% Begin initialization code - DO NOT EDIT

global out para

gui_Singleton = 1;
gui_State = struct('gui_Name',       mfilename, ...
                   'gui_Singleton',  gui_Singleton, ...
                   'gui_OpeningFcn', @TargetStrength_OpeningFcn, ...
                   'gui_OutputFcn',  @TargetStrength_OutputFcn, ...
                   'gui_LayoutFcn',  [] , ...
                   'gui_Callback',   []);
if nargin && ischar(varargin{1})
    gui_State.gui_Callback = str2func(varargin{1});
end

if nargout
    [varargout{1:nargout}] = gui_mainfcn(gui_State, varargin{:});
else
    gui_mainfcn(gui_State, varargin{:});
end
% End initialization code - DO NOT EDIT


% --- Executes just before TargetStrength is made visible.
function TargetStrength_OpeningFcn(hObject, eventdata, handles, varargin)
% This function has no output args, see OutputFcn.
% hObject    handle to figure
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)
% varargin   command line arguments to TargetStrength (see VARARGIN)

% Choose default command line output for TargetStrength
handles.output = hObject;

% Update handles structure
guidata(hObject, handles);

% UIWAIT makes TargetStrength wait for user response (see UIRESUME)
% uiwait(handles.figure1);


% --- Outputs from this function are returned to the command line.
function varargout = TargetStrength_OutputFcn(hObject, eventdata, handles) 
% varargout  cell array for returning output args (see VARARGOUT);
% hObject    handle to figure
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Get default command line output from handles structure
varargout{1} = handles.output;


% --- Executes on selection change in popupmenuTargetType.
function popupmenuTargetType_Callback(hObject, eventdata, handles)
% hObject    handle to popupmenuTargetType (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: contents = cellstr(get(hObject,'String')) returns popupmenuTargetType contents as cell array
%        contents{get(hObject,'Value')} returns selected item from popupmenuTargetType

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%% properties of Tungsten carbide - CRC handbook of Chemistry and Physics, David R. Lite, editor in chief
%%                                - 77th edition, 1996-1997, 14-36

switch get(handles.popupmenuTargetType,'value')
    case 1 % tungsten carbide  
       rho=14.9;				% density 
       cc=6853;					% speed of compressional wave 
       cs=4171;					% speed of shear wave  case 2
    case 2  % copper 
       rho=8.947;
       cc=4760;
       cs=2288.5;
    case 3  % aluminum
       rho=2.7;
       cc=6260;
       cs=3080;
    case 4 % tainless steel
       rho=7.8;
       cc=3.74*1500;
       cs=2.08*1500;
    otherwise
       rho=7.8;
       cc=3.74*1500;
       cs=2.08*1500;
      disp('OTHER')
end
set(handles.editRho,'string',rho);
set(handles.editCC,'string',cc);
set(handles.editCS,'string',cs);

return

% --- Executes during object creation, after setting all properties.
function popupmenuTargetType_CreateFcn(hObject, eventdata, handles)
% hObject    handle to popupmenuTargetType (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: popupmenu controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end



function editTemp_Callback(hObject, eventdata, handles)
% hObject    handle to editTemp (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of editTemp as text
%        str2double(get(hObject,'String')) returns contents of editTemp as a double


% --- Executes during object creation, after setting all properties.
function editTemp_CreateFcn(hObject, eventdata, handles)
% hObject    handle to editTemp (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end



function editSal_Callback(hObject, eventdata, handles)
% hObject    handle to editSal (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of editSal as text
%        str2double(get(hObject,'String')) returns contents of editSal as a double


% --- Executes during object creation, after setting all properties.
function editSal_CreateFcn(hObject, eventdata, handles)
% hObject    handle to editSal (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end



function editDep_Callback(hObject, eventdata, handles)
% hObject    handle to editDep (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of editDep as text
%        str2double(get(hObject,'String')) returns contents of editDep as a double


% --- Executes during object creation, after setting all properties.
function editDep_CreateFcn(hObject, eventdata, handles)
% hObject    handle to editDep (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end



function editDiam_Callback(hObject, eventdata, handles)
% hObject    handle to editDiam (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of editDiam as text
%        str2double(get(hObject,'String')) returns contents of editDiam as a double


% --- Executes during object creation, after setting all properties.
function editDiam_CreateFcn(hObject, eventdata, handles)
% hObject    handle to editDiam (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end


% --- Executes on button press in radiobutton12.
function radiobutton12_Callback(hObject, eventdata, handles)
% hObject    handle to radiobutton12 (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hint: get(hObject,'Value') returns toggle state of radiobutton12


% --- Executes on button press in radiobutton18.
function radiobutton18_Callback(hObject, eventdata, handles)
% hObject    handle to radiobutton18 (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hint: get(hObject,'Value') returns toggle state of radiobutton18


% --- Executes on button press in radiobutton38.
function radiobutton38_Callback(hObject, eventdata, handles)
% hObject    handle to radiobutton38 (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hint: get(hObject,'Value') returns toggle state of radiobutton38


% --- Executes on button press in radiobutton70.
function radiobutton70_Callback(hObject, eventdata, handles)
% hObject    handle to radiobutton70 (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hint: get(hObject,'Value') returns toggle state of radiobutton70


% --- Executes on button press in radiobutton50.
function radiobutton50_Callback(hObject, eventdata, handles)
% hObject    handle to radiobutton50 (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hint: get(hObject,'Value') returns toggle state of radiobutton50



function editLowFreq_Callback(hObject, eventdata, handles)
% hObject    handle to editLowFreq (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of editLowFreq as text
%        str2double(get(hObject,'String')) returns contents of editLowFreq as a double


% --- Executes during object creation, after setting all properties.
function editLowFreq_CreateFcn(hObject, eventdata, handles)
% hObject    handle to editLowFreq (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end


% --- Executes on button press in radiobutton120.
function radiobutton120_Callback(hObject, eventdata, handles)
% hObject    handle to radiobutton120 (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hint: get(hObject,'Value') returns toggle state of radiobutton120


% --- Executes on button press in radiobutton200.
function radiobutton200_Callback(hObject, eventdata, handles)
% hObject    handle to radiobutton200 (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hint: get(hObject,'Value') returns toggle state of radiobutton200


% --- Executes on button press in radiobutton333.
function radiobutton333_Callback(hObject, eventdata, handles)
% hObject    handle to radiobutton333 (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hint: get(hObject,'Value') returns toggle state of radiobutton333


% --- Executes on button press in radiobutton420.
function radiobutton420_Callback(hObject, eventdata, handles)
% hObject    handle to radiobutton420 (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hint: get(hObject,'Value') returns toggle state of radiobutton420


% --- Executes on button press in radiobuttonX.
function radiobuttonX_Callback(hObject, eventdata, handles)
% hObject    handle to radiobuttonX (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hint: get(hObject,'Value') returns toggle state of radiobuttonX



function editFreqX_Callback(hObject, eventdata, handles)
% hObject    handle to editFreqX (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of editFreqX as text
%        str2double(get(hObject,'String')) returns contents of editFreqX as a double


% --- Executes during object creation, after setting all properties.
function editFreqX_CreateFcn(hObject, eventdata, handles)
% hObject    handle to editFreqX (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end



function editHighFreq_Callback(hObject, eventdata, handles)
% hObject    handle to editHighFreq (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of editHighFreq as text
%        str2double(get(hObject,'String')) returns contents of editHighFreq as a double


% --- Executes during object creation, after setting all properties.
function editHighFreq_CreateFcn(hObject, eventdata, handles)
% hObject    handle to editHighFreq (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end


% --- Executes on button press in pushbuttonComp.
%function pushbuttonComp_Callback(hObject, eventdata, handles)
% % hObject    handle to pushbuttonComp (see GCBO)
% % eventdata  reserved - to be defined in a future version of MATLAB
% % handles    structure with handles and user data (see GUIDATA)
% 
% disp('ok')
% return

% --- Executes on button press in pushbuttonQuit.
function pushbuttonQuit_Callback(hObject, eventdata, handles)
% hObject    handle to pushbuttonQuit (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)
close all;
return


% --- Executes on button press in radiobutton17.
function radio_cw_rhow_Callback(hObject, eventdata, handles)
% hObject    handle to radiobutton17 (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hint: get(hObject,'Value') returns toggle state of radiobutton17
disp('radiobutton17')
return



function editValueFreqAve_Callback(hObject, eventdata, handles)
% hObject    handle to editValueFreqAve (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of editValueFreqAve as text
%        str2double(get(hObject,'String')) returns contents of editValueFreqAve as a double


% --- Executes during object creation, after setting all properties.
function editValueFreqAve_CreateFcn(hObject, eventdata, handles)
% hObject    handle to editValueFreqAve (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end



function editRho_Callback(hObject, eventdata, handles)
% hObject    handle to editRho (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of editRho as text
%        str2double(get(hObject,'String')) returns contents of editRho as a double


% --- Executes during object creation, after setting all properties.
function editRho_CreateFcn(hObject, eventdata, handles)
% hObject    handle to editRho (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end



function editCC_Callback(hObject, eventdata, handles)
% hObject    handle to editCC (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of editCC as text
%        str2double(get(hObject,'String')) returns contents of editCC as a double


% --- Executes during object creation, after setting all properties.
function editCC_CreateFcn(hObject, eventdata, handles)
% hObject    handle to editCC (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end



function editCS_Callback(hObject, eventdata, handles)
% hObject    handle to editCS (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of editCS as text
%        str2double(get(hObject,'String')) returns contents of editCS as a double


% --- Executes during object creation, after setting all properties.
function editCS_CreateFcn(hObject, eventdata, handles)
% hObject    handle to editCS (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end


function editNum_Callback(hObject, eventdata, handles)
% hObject    handle to editNum (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of editNum as text
%        str2double(get(hObject,'String')) returns contents of editNum as a double


% --- Executes during object creation, after setting all properties.
function editNum_CreateFcn(hObject, eventdata, handles)
% hObject    handle to editNum (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end



function edit_cw_Callback(hObject, eventdata, handles)
% hObject    handle to edit_cw (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of edit_cw as text
%        str2double(get(hObject,'String')) returns contents of edit_cw as a double


% --- Executes during object creation, after setting all properties.
function edit_cw_CreateFcn(hObject, eventdata, handles)
% hObject    handle to edit_cw (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end


function edit_rhow_Callback(hObject, eventdata, handles)
% hObject    handle to edit_rhow (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of edit_rhow as text
%        str2double(get(hObject,'String')) returns contents of edit_rhow as a double


% --- Executes during object creation, after setting all properties.
function edit_rhow_CreateFcn(hObject, eventdata, handles)
% hObject    handle to edit_rhow (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end


