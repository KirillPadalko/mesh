import logo from '../assets/logo.png';
import './SplashScreen.css';

export function SplashScreen() {
    return (
        <div className="splash-container">
            <img src={logo} alt="Mesh" className="splash-logo" />
        </div>
    );
}
