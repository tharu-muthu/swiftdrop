import { navigate } from '../app/navigation.js';

export default function AppLink({ href, children, onClick, ...props }) {
  function follow(event) {
    onClick?.(event);
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey
      || (props.target && props.target !== '_self') || props.download !== undefined) return;
    const destination = new URL(href, window.location.href);
    if (destination.origin !== window.location.origin) return;
    event.preventDefault();
    navigate(destination.pathname + destination.search + destination.hash);
  }
  return <a {...props} href={href} onClick={follow}>{children}</a>;
}
