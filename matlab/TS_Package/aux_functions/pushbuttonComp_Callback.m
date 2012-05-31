% --- Executes on button press in pushbuttonComp.
function pushbuttonComp_Callback(hObject, eventdata, handles)
% hObject    handle to pushbuttonComp (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

global out para

%% setup parameters
%%    freq_ange = [start_frequency  end_frequency] in kHz
%%    freq_spec = specific frequencies of interest in kHz
%%        scale = 1   Linear scale 
%%              = 2   Logarithmatic scale for gas bubble (swimbladder)
%%            n = number of output points
%% target_index = type of targets
%%              = 1   tungsten carbide
%%              = 2   copper
%%              = 3   aluminum
%%              = 4   stainless Steel
%%           D  = diameter of the sphere in mm
%%     T, P, S  = Temperature (deg C), Pressure (dbar), and Salinity (ppt)
%%      ave_BW  = BW of average over frequency (percentage, %)

%% get all parameters from the panel
ch0=get(gcf,'children');
ch0_names=get(ch0,'Tag');

%% Target Properties
id=find(strcmp(ch0_names,'uipanelTargetProperties') == 1);
ch1=get(ch0(id),'children');  
ch1_names=get(ch1,'Tag');
%% Target index
id=find(strcmp(ch1_names,'popupmenuTargetType') == 1);
target_index=get(ch1(id),'Value');
%% sphere diameter (mm)
id=find(strcmp(ch1_names,'editDiam') == 1);
D=str2num(get(ch1(id),'String'));

%Physical Properties
id=find(strcmp(ch1_names,'uipanelPhysicalProperties') == 1);
ch2=get(ch1(id),'ch');
para.rho=str2num(get(ch2(find(strcmp(get(ch2,'Tag'),'editRho') == 1)),'string'));
para.cc=str2num(get(ch2(find(strcmp(get(ch2,'Tag'),'editCC') == 1)),'string'));
para.cs=str2num(get(ch2(find(strcmp(get(ch2,'Tag'),'editCS') == 1)),'string'));

%% Variable
id=find(strcmp(ch0_names,'uipanelVariable') == 1);
ch1=get(ch0(id),'children');  
ch1_names=get(ch1,'Tag');
%% Frequency or Angle
id=find(strcmp(ch1_names,'radiobuttonVarFreq') == 1);
if get(ch1(id),'Value') == 1
   proc_flag=1;    % versus frequency
else
   proc_flag=2;    % versus angle
end

%% Environmental Parameters
id=find(strcmp(ch0_names,'uipanelEnvironmentalParameters') == 1);
ch1=get(ch0(id),'children');  
ch1_names=get(ch1,'Tag');

id=findobj('Tag','radio_TSD');

if get(id,'value')
    %% Temperature
    id=find(strcmp(ch1_names,'editTemp') == 1);
    T=str2num(get(ch1(id),'String'));
    %% Salinity
    id=find(strcmp(ch1_names,'editSal') == 1);
    S=str2num(get(ch1(id),'String'));
    %% Salinity
    id=find(strcmp(ch1_names,'editDep') == 1);
    P=str2num(get(ch1(id),'String'));
    cw=[];
    rhow=[];  
else
%    id=findobj('Tag','radio_cw_rhow');
    id=findobj('Tag','edit_cw');
    cw=str2num(get(id,'string'));
    id=findobj('Tag','edit_rhow');
    rhow=str2num(get(id,'string'));
    T=[];
    P=[];
    S=[];
end


%% Frequency Parameters
freq_spec0=[12 18 38 50 70 120 200 333 420];
freq_spec1=[];

id=find(strcmp(ch0_names,'uipanelFrequencyParameters') == 1);
ch1=get(ch0(id),'children');  
ch1_names=get(ch1,'Tag');
%% Discrete Frequencies
id=find(strcmp(ch1_names,'uipanelDiscreteFreq') == 1);
ch2=get(ch1(id),'children');  
ch2_names=get(ch2,'Tag');
freq_spec1=[];
for i=1:length(ch2)
  if get(ch2(i),'Value') == 1
      freq_spec1=[freq_spec1 str2num(get(ch2(i),'String'))];
  end
end
if ~isempty(freq_spec1)
   freq_spec=intersect(freq_spec1,freq_spec0);
else
   freq_spec=freq_spec1;
end
id=findobj(ch2,'Tag','radiobuttonX');
if get(id,'Value') == 1
    id1=findobj(ch2,'Tag','editFreqX');
    freq_spec=[freq_spec str2num(get(id1,'string'))];
end
    
%% Frequency Bandwidth
ch2=findobj(ch1,'Tag','uipanelFreqBandwidth');
ch3=get(ch2,'ch');
ch3_names=get(ch3,'Tag');
id=find(strcmp(ch3_names,'uipanelValue') == 1);
ch4=findobj(ch3(id),'Tag','editValueFreqAve');
para.ave_value=str2num(get(ch4,'string'));      % percentage or frequency in kHz
id=find(strcmp(ch3_names,'uipanelUnit') == 1);
ch4=findobj(ch3,'Tag','radiobuttonPercentFreqAve');
if get(ch4,'Value') == 1  % convert absolute freq to percent
   if ~isempty(freq_spec)
       ave_BW=0.01*para.ave_value*max(freq_spec);
   else
       ave_BW=para.ave_value;
   end
   para.ave_unit=1;    % average over specified freq BW
else
   ave_BW=para.ave_value;
   para.ave_unit=0;    % average over a fixed percentage frequency over the entire frequency range 
end

%% Freqency Range
if proc_flag == 1
  id=find(strcmp(ch1_names,'uipanelFreqRange') == 1);
else
  freq_range=min(freq_spec);
end
ch2L=findobj(ch1,'Tag','editLowFreq');
freq_range0(1)=str2num(get(ch2L,'string'));
ch2H=findobj(ch1,'Tag','editHighFreq');
freq_range0(2)=str2num(get(ch2H,'string'));

if isempty(freq_spec)
    freq_range(1)=max(0.01,freq_range0(1)*(1-0.01*max(ave_BW/2)));
    set(ch2L,'string',floor(10*freq_range(1))/10);
    freq_range(2)=freq_range0(2)*(1+0.01*max(ave_BW/2));
    set(ch2H,'string',ceil(10*freq_range(2))/10);
else
    if para.ave_unit == 1
       freq_range(1)=max(0.01,min(min(freq_spec),freq_range0(1))*(1-0.01*max(ave_BW/2)));
    else
       freq_range(1)=max(0.01,min(min(freq_spec),freq_range0(1))-max(ave_BW/2));
    end
    if freq_range(1) < 0.1
       set(ch2L,'string',floor(100*freq_range(1))/100);
    else
       set(ch2L,'string',floor(10*freq_range(1))/10);
    end
    if para.ave_unit == 1
        freq_range(2)=max(max(freq_spec),freq_range0(2)*(1+0.01*max(ave_BW/2)));
    else
        freq_range(2)=max(max(freq_spec),freq_range0(2))+max(ave_BW/2);
    end
    if  freq_range(2) < 0.1
        set(ch2H,'string',ceil(100*freq_range(2))/100);
    else
        set(ch2H,'string',ceil(10*freq_range(2))/10);
    end
end

%% linear freq scale
scale=1;                % 1 = linear, 2 = log
ch2=findobj(ch1,'Tag','editNum');
n=str2num(get(ch2,'string'));
para.n=n;
if ~isempty(freq_spec)
    freq0=num2str(freq_spec(:));
else
    freq0=freq_range0(1);
end
kHz=' kHz';
freq_str=[num2str(freq0) kHz(ones(size(freq0,1),1),:)];
[para,out]=solid_elastic_sphere_TS_fun(freq_range,freq_spec,scale,n,target_index,proc_flag,D,T,P,S,cw,rhow,ave_BW,para);

para.freq_range=freq_range;
para.freq_spec=freq_spec;
para.ave_BW=ave_BW;
para.scale=scale;
para.n=n;
para.target_index=target_index;
para.proc_flag=proc_flag;
para.D=D;
para.T=T;
para.P=P;
para.S=S;

if para.proc_flag == 1
    h(1) = figure;
    clf
    [ax, h1, h2] = plotyy(out.freq, out.TS, out.freq, out.phase);
    set(h1, 'Color', 'b','markersize', 10, 'linewidth', 2)
    set(h2, 'markersize', 10, 'linewidth', 2)
    hold on
    plot(ax(1), out.freq, out.TS_ave, '-g', 'markersize', 10, 'linewidth', 2)
    plot(ax(1), para.freq_spec, out.TS_spec, 'hr', 'markersize', 10, 'linewidth', 2)
    grid on
    xlabel('FREQUENCY (kHz)','fontweight','bold','fontsize',14)
    set(get(ax(1), 'YLabel'), 'String', 'TARGET STRENGTH (dB)','fontweight','bold','fontsize',14)
    set(get(ax(2), 'YLabel'), 'String', 'TARGET PHASE (\circ)','fontweight','bold','fontsize',14)
    legend('Theory (no ave)', 'Theory (with ave)', 4)
%    text(freq0-2,out.TS_spec-2,freq_str,'color','r');
    title([out.t_str ' (' sprintf('%4.3g mm',D) ')'],'fontweight','bold','fontsize',14);
    axis(ax(1), [freq_range min(-80,min(out.TS)-5) max(-30,max(out.TS)+5)])
    xlim(ax(2), freq_range)
    set(ax(1), 'YTickMode', 'auto') % plotyy turns this off, which causes problems when zooming
    set(ax(2), 'YTickMode', 'auto')
    fprintf('\tfreq (kHz)\t\t TS\t\t TS (ave)\n')
    for i=1:length(freq_spec)
        fprintf('\t\t%d\t\t %8.3f\t %8.3f\n',para.freq_spec(i),out.TS_spec(i),out.TS_spec_ave(i));
    end
else
    h(1) = figure;
    clf
    subplot(1,2,1)
    plot(out.theta*180/pi,out.TS,'linewidth',1.5);
    ylabel('TARGET STRENGTH (dB)','fontweight','bold','fontsize',14)
    xlabel('SCATTERING ANGLE (deg)','fontweight','bold','fontsize',14)
    title(sprintf('%6.2f (kHz)',out.freq),'fontweight','bold','fontsize',14)
    grid on
    subplot(1,2,2)
    plot((out.theta*180/pi)-180,fftshift(out.TS),'linewidth',1.5);
    ylabel('TARGET STRENGTH (dB)','fontweight','bold','fontsize',14)
    xlabel('SCATTERING ANGLE (deg)','fontweight','bold','fontsize',14)
    title(sprintf('%6.2f (kHz)',out.freq),'fontweight','bold','fontsize',14)
    grid on
    h(2) = figure;
    polar(out.theta,out.fm);
    grid on
    title('SCATTERING AMPLITUDE DIRECTIVITY','fontweight','bold','fontsize',14)
    xlabel(sprintf('%6.2f (kHz)',out.freq),'fontweight','bold','fontsize',14)
end

% Store the handles of the figures that were created, to be used to delete them later.
handles.figuresCreated = h;
guidata(hObject, handles)

return