import Brand from '../components/Brand.jsx';
import './app-loading.css';

export default function AppLoading() {
  return <main className="app-loading" aria-busy="true">
    <div className="app-loading-brand" aria-hidden="true">
      <Brand />
      <div className="app-loading-track"><span /></div>
    </div>
    <span className="app-loading-announcement" role="status">Loading SwiftDrop</span>
  </main>;
}
