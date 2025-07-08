import React, { useState, useEffect, createContext, useContext, useRef } from 'react';
import { Search, Menu, MoreVertical, Plus, ArrowLeft, Paperclip, Mic, Send, Folder, File, GitMerge, Cpu, Activity, Settings, CheckCircle, AlertCircle, XCircle, ChevronRight, Bell, Code, Copy, Users, Shield, Power, Sun, Moon, Smartphone } from 'lucide-react';

// Design System & Theme Configuration
const themes = {
  light: {
    primary: '#00C853',
    onPrimary: '#FFFFFF',
    primaryContainer: '#C8E6C9',
    onPrimaryContainer: '#002106',
    secondary: '#5D6B5D',
    background: '#F7F2F2',
    surface: '#FFFFFF',
    surfaceVariant: '#E0E0E0',
    error: '#BA1A1A',
    onError: '#FFFFFF',
    onBackground: '#1A1C1A',
    onSurface: '#1A1C1A',
    onSurfaceVariant: '#42493F',
    outline: '#72796F',
    success: '#28a745',
  },
  dark: {
    primary: '#00E676',
    onPrimary: '#003910',
    primaryContainer: '#005319',
    onPrimaryContainer: '#C8E6C9',
    secondary: '#B9CCB9',
    background: '#121212',
    surface: '#1E1E1E',
    surfaceVariant: '#42493F',
    error: '#FFB4AB',
    onError: '#690005',
    onBackground: '#E1E3DF',
    onSurface: '#E1E3DF',
    onSurfaceVariant: '#C2C8BD',
    outline: '#8C9388',
    success: '#20c997',
  }
};

const ThemeContext = createContext();

const ThemeProvider = ({ children }) => {
  const [theme, setTheme] = useState('light');

  useEffect(() => {
    const root = window.document.documentElement;
    const currentTheme = themes[theme];
    
    root.style.setProperty('--primary', currentTheme.primary);
    root.style.setProperty('--on-primary', currentTheme.onPrimary);
    root.style.setProperty('--primary-container', currentTheme.primaryContainer);
    root.style.setProperty('--on-primary-container', currentTheme.onPrimaryContainer);
    root.style.setProperty('--secondary', currentTheme.secondary);
    root.style.setProperty('--background', currentTheme.background);
    root.style.setProperty('--surface', currentTheme.surface);
    root.style.setProperty('--surface-variant', currentTheme.surfaceVariant);
    root.style.setProperty('--error', currentTheme.error);
    root.style.setProperty('--on-error', currentTheme.onError);
    root.style.setProperty('--on-background', currentTheme.onBackground);
    root.style.setProperty('--on-surface', currentTheme.onSurface);
    root.style.setProperty('--on-surface-variant', currentTheme.onSurfaceVariant);
    root.style.setProperty('--outline', currentTheme.outline);
    root.style.setProperty('--success', currentTheme.success);
    
    if (theme === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }, [theme]);

  return (
    <ThemeContext.Provider value={{ theme, setTheme }}>
      {children}
    </ThemeContext.Provider>
  );
};

// Mock Data
const initialProjects = [
  { id: 1, name: 'Project Phoenix', path: '/storage/emulated/0/dev/phoenix', lastActivity: '5m ago', status: 'Active', connection: 'connected' },
  { id: 2, name: 'QuantumLeap API', path: '/storage/emulated/0/dev/ql-api', lastActivity: '2h ago', status: 'Idle', connection: 'connected' },
  { id: 3, name: 'DataWeaver', path: '/storage/emulated/0/data/weaver', lastActivity: '1d ago', status: 'Idle', connection: 'error' },
  { id: 4, name: 'Mobile UI Kit', path: '/storage/emulated/0/dev/ui-kit', lastActivity: '3d ago', status: 'Idle', connection: 'connecting' },
];

const chatMessages = [
    { type: 'system', text: 'Connection established. Agent is ready.' },
    { type: 'user', text: 'Hey, can you refactor the main component to use hooks?' },
    { type: 'agent_typing' },
    { type: 'agent', text: 'Of course. Here is the refactored `MainComponent`:' },
    { type: 'code', content: `import React, { useState, useEffect } from 'react';\n\nconst MainComponent = () => {\n  const [data, setData] = useState(null);\n\n  useEffect(() => {\n    fetchData().then(setData);\n  }, []);\n\n  return <div>{data ? data.message : 'Loading...'}</div>;\n};` },
    { type: 'permission_request', title: 'Permission Required', description: 'Agent wants to write to `MainComponent.js`.', details: 'WRITE /dev/phoenix/src/components/MainComponent.js' },
    { type: 'user', text: 'Looks good. Please proceed.' },
    { type: 'progress', text: 'Applying changes...', percentage: 75 },
    { type: 'file_summary', text: 'Successfully updated 1 file.', status: 'success' },
    { type: 'error', text: 'Failed to run tests after update. Check logs.' },
    { type: 'agent', text: 'Is there anything else I can help you with?' },
    { type: 'user', text: 'Yes, can you check the git status for me?' },
    { type: 'agent_typing' },
];

const fileSystem = {
    '/': [
        { name: '.git', type: 'folder' },
        { name: 'src', type: 'folder' },
        { name: 'package.json', type: 'file', git: 'modified', size: '1.2 KB', modified: '2m ago' },
        { name: 'README.md', type: 'file', git: 'none', size: '3.4 KB', modified: '1d ago' },
    ],
    '/src': [
        { name: 'components', type: 'folder' },
        { name: 'App.js', type: 'file', git: 'new', size: '5.8 KB', modified: '1h ago' },
        { name: 'index.css', type: 'file', git: 'deleted', size: '0.5 KB', modified: '2h ago' },
    ],
    '/src/components': [
        { name: 'MainComponent.js', type: 'file', git: 'modified', size: '2.1 KB', modified: '5m ago' },
    ]
};

// Reusable Components
const TopAppBar = ({ title, onNavIconClick, actions, showBackButton, onBackClick }) => (
    <header className="flex-shrink-0 flex items-center justify-between p-4 bg-surface dark:bg-surface text-onSurface dark:text-onSurface shadow-md h-16 z-20">
        <div className="flex items-center">
            {showBackButton ? (
                <button onClick={onBackClick} className="p-2 rounded-full hover:bg-surfaceVariant dark:hover:bg-surfaceVariant mr-2"><ArrowLeft /></button>
            ) : (
                <button onClick={onNavIconClick} className="p-2 rounded-full hover:bg-surfaceVariant dark:hover:bg-surfaceVariant mr-2"><Menu /></button>
            )}
            <h1 className="text-xl font-medium">{title}</h1>
        </div>
        <div className="flex items-center space-x-2">
            {actions}
        </div>
    </header>
);

const FAB = ({ onClick, icon }) => (
    <button onClick={onClick} className="absolute bottom-24 right-4 w-14 h-14 bg-primaryContainer dark:bg-primaryContainer text-onPrimaryContainer dark:text-onPrimaryContainer rounded-2xl shadow-lg flex items-center justify-center transition-transform hover:scale-105 z-10">
        {icon}
    </button>
);

const ProjectCard = ({ project, onClick }) => {
    const connectionColors = {
        connected: 'bg-green-500',
        connecting: 'bg-yellow-500',
        error: 'bg-red-500',
    };
    return (
        <div onClick={onClick} className="bg-surface dark:bg-surface rounded-2xl p-4 mb-4 shadow-sm hover:shadow-lg transition-shadow cursor-pointer border border-outline/20 dark:border-outline/40">
            <div className="flex justify-between items-start">
                <div>
                    <div className="flex items-center mb-1">
                        <div className={`w-3 h-3 rounded-full mr-2 ${connectionColors[project.connection]}`}></div>
                        <h2 className="text-lg font-bold text-onSurface dark:text-onSurface">{project.name}</h2>
                    </div>
                    <p className="text-sm text-secondary dark:text-secondary">{project.path}</p>
                </div>
                <button className="p-2 rounded-full hover:bg-surfaceVariant dark:hover:bg-surfaceVariant"><MoreVertical size={20} className="text-onSurfaceVariant dark:text-onSurfaceVariant" /></button>
            </div>
            <div className="flex justify-between items-center mt-4 text-sm text-onSurfaceVariant dark:text-onSurfaceVariant">
                <span>{project.status}</span>
                <span>{project.lastActivity}</span>
            </div>
        </div>
    );
};

const BottomNavBar = ({ activeTab, onTabChange }) => {
    const tabs = [
        { name: 'Chat', icon: <Bell size={24} /> },
        { name: 'Files', icon: <Folder size={24} /> },
        { name: 'Monitor', icon: <Activity size={24} /> },
        { name: 'Settings', icon: <Settings size={24} /> },
    ];

    return (
        <nav className="flex-shrink-0 h-20 bg-surfaceVariant dark:bg-surfaceVariant shadow-inner flex justify-around items-center z-20">
            {tabs.map(tab => (
                <button key={tab.name} onClick={() => onTabChange(tab.name)} className={`flex flex-col items-center justify-center w-full h-full transition-colors ${activeTab === tab.name ? 'text-primary dark:text-primary' : 'text-onSurfaceVariant dark:text-onSurfaceVariant'}`}>
                    <div className={`p-1 rounded-full ${activeTab === tab.name ? 'bg-primaryContainer dark:bg-primaryContainer' : ''}`}>
                       {React.cloneElement(tab.icon, { color: activeTab === tab.name ? 'var(--on-primary-container)' : 'var(--on-surface-variant)' })}
                    </div>
                    <span className="text-xs mt-1">{tab.name}</span>
                </button>
            ))}
        </nav>
    );
};

const CreateProjectDialog = ({ onDismiss, onCreate }) => {
    const [name, setName] = useState('');
    const [path, setPath] = useState('');

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
            <div className="bg-surface dark:bg-surface rounded-3xl p-6 w-full max-w-sm shadow-xl">
                <h2 className="text-2xl font-medium text-onSurface dark:text-onSurface mb-4">New Project</h2>
                <div className="space-y-4">
                    <input type="text" placeholder="Project Name" value={name} onChange={e => setName(e.target.value)} className="w-full bg-surfaceVariant dark:bg-surfaceVariant text-onSurfaceVariant dark:text-onSurfaceVariant p-3 rounded-lg border border-outline dark:border-outline focus:outline-none focus:ring-2 focus:ring-primary" />
                    <input type="text" placeholder="Project Path" value={path} onChange={e => setPath(e.target.value)} className="w-full bg-surfaceVariant dark:bg-surfaceVariant text-onSurfaceVariant dark:text-onSurfaceVariant p-3 rounded-lg border border-outline dark:border-outline focus:outline-none focus:ring-2 focus:ring-primary" />
                </div>
                <div className="flex justify-end mt-6 space-x-4">
                    <button onClick={onDismiss} className="text-primary dark:text-primary px-4 py-2 rounded-full hover:bg-primary/10">Cancel</button>
                    <button onClick={() => onCreate({ name, path })} className="bg-primary dark:bg-primary text-onPrimary dark:text-onPrimary px-6 py-2 rounded-full shadow-sm hover:shadow-md">Create</button>
                </div>
            </div>
        </div>
    );
};

// Chat Tab Components
const ChatMessage = ({ msg }) => {
    const copyToClipboard = (text) => {
        const textArea = document.createElement("textarea");
        textArea.value = text;
        document.body.appendChild(textArea);
        textArea.select();
        try {
            document.execCommand('copy');
        } catch (err) {
            console.error('Failed to copy text: ', err);
        }
        document.body.removeChild(textArea);
    };

    switch (msg.type) {
        case 'user':
            return <div className="flex justify-end mb-4"><div className="bg-primaryContainer dark:bg-primaryContainer text-onPrimaryContainer dark:text-onPrimaryContainer rounded-2xl rounded-br-lg p-3 max-w-[85%]">{msg.text}</div></div>;
        case 'agent':
            return <div className="flex justify-start mb-4"><div className="bg-surfaceVariant dark:bg-surfaceVariant text-onSurfaceVariant dark:text-onSurfaceVariant rounded-2xl rounded-bl-lg p-3 max-w-[85%]">{msg.text}</div></div>;
        case 'agent_typing':
            return <div className="flex justify-start mb-4"><div className="bg-surfaceVariant dark:bg-surfaceVariant text-onSurfaceVariant dark:text-onSurfaceVariant rounded-2xl rounded-bl-lg p-3 max-w-[85%]"><div className="flex items-center space-x-1"><div className="w-2 h-2 bg-onSurfaceVariant/50 rounded-full animate-bounce"></div><div className="w-2 h-2 bg-onSurfaceVariant/50 rounded-full animate-bounce [animation-delay:0.2s]"></div><div className="w-2 h-2 bg-onSurfaceVariant/50 rounded-full animate-bounce [animation-delay:0.4s]"></div></div></div></div>;
        case 'system':
            return <div className="text-center text-sm text-secondary dark:text-secondary my-4">{msg.text}</div>;
        case 'error':
            return <div className="flex justify-start mb-4"><div className="bg-error/20 text-error rounded-lg p-3 flex items-center gap-2"><AlertCircle size={18} />{msg.text}</div></div>;
        case 'code':
            return (
                <div className="bg-surfaceVariant dark:bg-surfaceVariant rounded-lg my-2 overflow-hidden">
                    <div className="bg-black/20 p-2 flex justify-between items-center">
                        <span className="text-xs text-onSurfaceVariant">JavaScript</span>
                        <button onClick={() => copyToClipboard(msg.content)} className="flex items-center gap-1 text-xs text-onSurfaceVariant hover:text-primary"><Copy size={14} /> Copy</button>
                    </div>
                    <pre className="p-3 text-sm overflow-x-auto"><code className="font-mono text-onSurfaceVariant">{msg.content}</code></pre>
                </div>
            );
        case 'permission_request':
            return (
                <div className="bg-surface dark:bg-surface border border-outline dark:border-outline rounded-2xl p-4 my-4">
                    <h3 className="font-bold text-onSurface dark:text-onSurface flex items-center gap-2"><Shield size={18} /> {msg.title}</h3>
                    <p className="text-sm text-onSurfaceVariant dark:text-onSurfaceVariant my-2">{msg.description}</p>
                    <code className="text-xs bg-surfaceVariant dark:bg-surfaceVariant p-2 rounded-md block font-mono my-2">{msg.details}</code>
                    <div className="flex justify-end gap-2 mt-4">
                        <button className="text-primary dark:text-primary px-4 py-2 rounded-full hover:bg-primary/10 text-sm">Deny</button>
                        <button className="bg-primary dark:bg-primary text-onPrimary dark:text-onPrimary px-4 py-2 rounded-full shadow-sm text-sm">Allow</button>
                    </div>
                </div>
            );
        case 'progress':
            return (
                <div className="my-4">
                    <p className="text-sm text-center text-secondary dark:text-secondary mb-2">{msg.text}</p>
                    <div className="w-full bg-surfaceVariant dark:bg-surfaceVariant rounded-full h-2.5">
                        <div className="bg-primary dark:bg-primary h-2.5 rounded-full" style={{ width: `${msg.percentage}%` }}></div>
                    </div>
                </div>
            );
        case 'file_summary':
            return (
                <div className={`flex items-center justify-center gap-2 text-sm my-4 p-2 rounded-lg ${msg.status === 'success' ? 'bg-green-500/20 text-green-700 dark:text-green-300' : 'bg-red-500/20 text-red-700 dark:text-red-300'}`}>
                    {msg.status === 'success' ? <CheckCircle size={16} /> : <XCircle size={16} />}
                    {msg.text}
                </div>
            )
        default:
            return null;
    }
};

// Main Screens
const ProjectsListScreen = ({ onProjectSelect, onAddProject }) => {
    const [projects, setProjects] = useState([]);
    
    useEffect(() => {
        setTimeout(() => setProjects(initialProjects), 500);
    }, []);

    return (
        <div className="bg-background dark:bg-background h-full text-onBackground dark:text-onBackground">
            <TopAppBar
                title="Pocket Agent"
                actions={<button className="p-2 rounded-full hover:bg-surfaceVariant dark:hover:bg-surfaceVariant"><Search /></button>}
            />
            <main className="p-4 overflow-y-auto h-[calc(100%-64px)]">
                {projects.length > 0 ? (
                    projects.map(p => <ProjectCard key={p.id} project={p} onClick={() => onProjectSelect(p)} />)
                ) : (
                    <div className="flex flex-col items-center justify-center h-full text-center">
                        <Users size={64} className="text-secondary dark:text-secondary mb-4" />
                        <h2 className="text-xl font-medium text-onSurface dark:text-onSurface">No projects yet</h2>
                        <p className="text-secondary dark:text-secondary mt-2 mb-6">Create your first project to get started.</p>
                        <button onClick={onAddProject} className="bg-primary dark:bg-primary text-onPrimary dark:text-onPrimary px-6 py-3 rounded-full shadow-sm hover:shadow-md flex items-center gap-2">
                            <Plus size={20} /> Create Project
                        </button>
                    </div>
                )}
            </main>
            <FAB onClick={onAddProject} icon={<Plus size={24} />} />
        </div>
    );
};

const ProjectDashboardScreen = ({ project, onBack }) => {
    const [activeTab, setActiveTab] = useState('Chat');
    const contentRef = useRef(null);

    useEffect(() => {
        if (activeTab === 'Chat' && contentRef.current) {
            contentRef.current.scrollTop = contentRef.current.scrollHeight;
        }
    }, [activeTab, chatMessages]);

    const renderTabContent = () => {
        switch (activeTab) {
            case 'Chat':
                return <div className="p-4">{chatMessages.map((msg, i) => <ChatMessage key={i} msg={msg} />)}</div>;
            case 'Files':
                return <FilesTab />;
            case 'Monitor':
                return <MonitorTab />;
            case 'Settings':
                return <SettingsTab />;
            default:
                return null;
        }
    };

    return (
        <div className="bg-background dark:bg-background h-full flex flex-col">
            <TopAppBar
                title={project.name}
                showBackButton={true}
                onBackClick={onBack}
                actions={<div className="bg-green-500/20 text-green-700 dark:text-green-300 text-xs px-3 py-1 rounded-full flex items-center gap-1"><div className="w-2 h-2 bg-green-500 rounded-full"></div>Active</div>}
            />
            <main ref={contentRef} className="flex-1 overflow-y-auto bg-background dark:bg-background">
                {renderTabContent()}
            </main>
            
            {activeTab === 'Chat' && (
                <div className="flex-shrink-0 bg-surface dark:bg-surface p-2 border-t border-outline/20 dark:border-outline/40">
                    <div className="flex items-center">
                        <button className="p-3 rounded-full hover:bg-surfaceVariant dark:hover:bg-surfaceVariant"><Paperclip size={24} className="text-onSurfaceVariant dark:text-onSurfaceVariant" /></button>
                        <input type="text" placeholder="Message" className="flex-grow bg-transparent p-3 focus:outline-none text-onSurface dark:text-onSurface" />
                        <button className="p-3 rounded-full hover:bg-surfaceVariant dark:hover:bg-surfaceVariant"><Mic size={24} className="text-onSurfaceVariant dark:text-onSurfaceVariant opacity-50" /></button>
                        <button className="p-3 bg-primary dark:bg-primary rounded-full text-onPrimary dark:text-onPrimary"><Send size={24} /></button>
                    </div>
                </div>
            )}
            
            <BottomNavBar activeTab={activeTab} onTabChange={setActiveTab} />
        </div>
    );
};

const FilesTab = () => {
    const [path, setPath] = useState('/');
    const files = fileSystem[path] || [];

    const getGitStatusColor = (status) => {
        switch(status) {
            case 'modified': return 'text-blue-500';
            case 'new': return 'text-green-500';
            case 'deleted': return 'text-red-500';
            default: return 'text-transparent';
        }
    };

    return (
        <div className="p-4">
            <div className="text-sm text-secondary dark:text-secondary mb-4">
                <span onClick={() => setPath('/')} className="cursor-pointer hover:underline">root</span>
                {path.substring(1).split('/').filter(Boolean).map((p, i, arr) => (
                    <span key={i}> / <span className="cursor-pointer hover:underline" onClick={() => setPath('/' + arr.slice(0, i + 1).join('/'))}>{p}</span></span>
                ))}
            </div>
            <ul>
                {path !== '/' && <li onClick={() => setPath(path.substring(0, path.lastIndexOf('/')) || '/')} className="flex items-center p-3 rounded-lg hover:bg-surfaceVariant dark:hover:bg-surfaceVariant cursor-pointer"><Folder size={20} className="mr-4 text-secondary dark:text-secondary" />..</li>}
                {files.map(file => (
                    <li key={file.name} onClick={() => file.type === 'folder' && setPath(path === '/' ? `/${file.name}`: `${path}/${file.name}`)} className="flex items-center p-3 rounded-lg hover:bg-surfaceVariant dark:hover:bg-surfaceVariant cursor-pointer">
                        {file.type === 'folder' ? <Folder size={20} className="mr-4 text-secondary dark:text-secondary" /> : <File size={20} className="mr-4 text-secondary dark:text-secondary" />}
                        <span className="flex-grow text-onSurface dark:text-onSurface">{file.name}</span>
                        <GitMerge size={16} className={`mr-4 ${getGitStatusColor(file.git)}`} />
                        <span className="text-sm text-secondary dark:text-secondary">{file.size || ''}</span>
                    </li>
                ))}
            </ul>
        </div>
    );
};

const MonitorTab = () => (
    <div className="p-4 space-y-6">
        <div className="bg-surface dark:bg-surface p-4 rounded-2xl border border-outline/20 dark:border-outline/40">
            <h3 className="font-bold text-onSurface dark:text-onSurface mb-4 flex items-center gap-2"><Cpu size={20} /> Active Processes</h3>
            <div className="space-y-3">
                <div className="flex items-center">
                    <span className="flex-grow text-onSurfaceVariant dark:text-onSurfaceVariant">NodeJS Server</span>
                    <div className="w-20 h-2 bg-surfaceVariant dark:bg-surfaceVariant rounded-full mr-2"><div className="w-1/2 bg-primary dark:bg-primary h-2 rounded-full"></div></div>
                    <span className="text-sm text-secondary dark:text-secondary">50%</span>
                </div>
                 <div className="flex items-center">
                    <span className="flex-grow text-onSurfaceVariant dark:text-onSurfaceVariant">File Watcher</span>
                    <div className="w-20 h-2 bg-surfaceVariant dark:bg-surfaceVariant rounded-full mr-2"><div className="w-1/4 bg-primary dark:bg-primary h-2 rounded-full"></div></div>
                    <span className="text-sm text-secondary dark:text-secondary">25%</span>
                </div>
            </div>
        </div>
        <div className="bg-surface dark:bg-surface p-4 rounded-2xl border border-outline/20 dark:border-outline/40">
            <h3 className="font-bold text-onSurface dark:text-onSurface mb-4 flex items-center gap-2"><Activity size={20} /> Recent Activities</h3>
            <ul className="space-y-3">
                <li className="flex items-center text-sm"><CheckCircle size={16} className="text-green-500 mr-3" /> <span className="text-onSurfaceVariant dark:text-onSurfaceVariant">Updated MainComponent.js</span><span className="ml-auto text-secondary dark:text-secondary">2m ago</span></li>
                <li className="flex items-center text-sm"><XCircle size={16} className="text-red-500 mr-3" /> <span className="text-onSurfaceVariant dark:text-onSurfaceVariant">Test run failed</span><span className="ml-auto text-secondary dark:text-secondary">1m ago</span></li>
                <li className="flex items-center text-sm"><CheckCircle size={16} className="text-green-500 mr-3" /> <span className="text-onSurfaceVariant dark:text-onSurfaceVariant">Git commit "Refactor component"</span><span className="ml-auto text-secondary dark:text-secondary">30s ago</span></li>
            </ul>
        </div>
    </div>
);

const SettingsTab = () => {
    const { theme, setTheme } = useContext(ThemeContext);

    return (
        <div className="p-4 space-y-6">
            <div className="bg-surface dark:bg-surface rounded-2xl border border-outline/20 dark:border-outline/40">
                <h3 className="font-bold text-onSurface dark:text-onSurface p-4 flex items-center gap-2"><Power size={20} /> Connection</h3>
                <div className="p-4 border-t border-outline/20 dark:border-outline/40">
                    <div className="flex justify-between items-center">
                        <span className="text-onSurfaceVariant dark:text-onSurfaceVariant">Status</span>
                        <span className="text-green-500">Connected</span>
                    </div>
                </div>
            </div>
            <div className="bg-surface dark:bg-surface rounded-2xl border border-outline/20 dark:border-outline/40">
                <h3 className="font-bold text-onSurface dark:text-onSurface p-4 flex items-center gap-2"><Smartphone size={20} /> Theme</h3>
                <div className="p-4 border-t border-outline/20 dark:border-outline/40 space-y-4">
                    <div className="flex justify-between items-center">
                        <span className="text-onSurfaceVariant dark:text-onSurfaceVariant">Theme Mode</span>
                        <div className="flex items-center bg-surfaceVariant dark:bg-surfaceVariant p-1 rounded-full">
                            <button onClick={() => setTheme('light')} className={`p-2 rounded-full ${theme === 'light' ? 'bg-primaryContainer text-onPrimaryContainer' : ''}`}><Sun size={18}/></button>
                            <button onClick={() => setTheme('dark')} className={`p-2 rounded-full ${theme === 'dark' ? 'bg-primaryContainer text-onPrimaryContainer' : ''}`}><Moon size={18}/></button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

// Main App Component
export default function App() {
    const [currentScreen, setCurrentScreen] = useState('projects');
    const [selectedProject, setSelectedProject] = useState(null);
    const [showDialog, setShowDialog] = useState(false);

    const handleProjectSelect = (project) => {
        setSelectedProject(project);
        setCurrentScreen('dashboard');
    };
    
    const handleBackToProjects = () => {
        setSelectedProject(null);
        setCurrentScreen('projects');
    };

    const handleAddProject = () => {
        setShowDialog(true);
    };

    const handleCreateProject = (newProject) => {
        const project = { ...newProject, id: Date.now(), lastActivity: 'Just now', status: 'Idle', connection: 'connecting' };
        // In a real app, you'd update state properly
        console.log("Creating project:", project);
        setShowDialog(false);
        handleProjectSelect(project);
    };

    return (
        <ThemeProvider>
            <div className="font-sans antialiased w-full h-full bg-background dark:bg-background">
                <div className="max-w-md mx-auto h-screen overflow-hidden shadow-2xl relative bg-surface dark:bg-surface flex flex-col">
                    {currentScreen === 'projects' && <ProjectsListScreen onProjectSelect={handleProjectSelect} onAddProject={handleAddProject} />}
                    {currentScreen === 'dashboard' && <ProjectDashboardScreen project={selectedProject} onBack={handleBackToProjects} />}
                    {showDialog && <CreateProjectDialog onDismiss={() => setShowDialog(false)} onCreate={handleCreateProject} />}
                </div>
            </div>
        </ThemeProvider>
    );
}
